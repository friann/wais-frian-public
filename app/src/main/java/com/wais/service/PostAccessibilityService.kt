package com.wais.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.wais.accessibility.AccessibilityTextStore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class PostAccessibilityService : AccessibilityService() {
    private val tag = "A11yService"
    private var lastStoredHash: Int = 0

    private val blockedPackages = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.wais"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg in blockedPackages) return

        val root = rootInActiveWindow ?: return
        val extracted = extractRawText(root)
        
        if (extracted.isNotBlank()) {
            val hash = extracted.hashCode()
            if (hash != lastStoredHash && extracted.length > 50) {
                lastStoredHash = hash
                AccessibilityTextStore.update(extracted)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    sealed interface CaptureResult {
        /** Base64 (no wrap) JPEG, downscaled for upload. */
        data class Success(val jpegBase64: String) : CaptureResult
        data class Failure(val message: String) : CaptureResult
    }

    /** Captures the current screen. Callers must hide their own overlays first. */
    suspend fun captureScreenshotJpeg(): CaptureResult = suspendCancellableCoroutine { cont ->
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(this),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val result = try {
                            encode(screenshot)
                        } catch (t: Throwable) {
                            Log.e(tag, "Screenshot encode failed", t)
                            CaptureResult.Failure("Could not process the screenshot.")
                        }
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(tag, "takeScreenshot failed: $errorCode")
                        val message = when (errorCode) {
                            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                                "Please wait a moment before checking another image."
                            else -> "Could not capture this screen. Some apps block screenshots."
                        }
                        if (cont.isActive) cont.resume(CaptureResult.Failure(message))
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(tag, "takeScreenshot threw", t)
            if (cont.isActive) cont.resume(CaptureResult.Failure("Screenshot is unavailable."))
        }
    }

    private fun encode(shot: ScreenshotResult): CaptureResult {
        val buffer = shot.hardwareBuffer
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, shot.colorSpace)
                ?: return CaptureResult.Failure("Could not read the screenshot.")
            val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()

            val longEdge = maxOf(software.width, software.height)
            val scaled = if (longEdge > MAX_IMAGE_EDGE) {
                val ratio = MAX_IMAGE_EDGE.toFloat() / longEdge
                Bitmap.createScaledBitmap(
                    software,
                    (software.width * ratio).toInt(),
                    (software.height * ratio).toInt(),
                    true
                ).also { software.recycle() }
            } else software

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            scaled.recycle()
            return CaptureResult.Success(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
        } finally {
            buffer.close()
        }
    }

    companion object {
        private const val MAX_IMAGE_EDGE = 1280

        /** Set while the accessibility service is connected; null otherwise. */
        @Volatile
        var instance: PostAccessibilityService? = null
            private set
    }

    private fun extractRawText(root: AccessibilityNodeInfo): String {
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        val screenHeight = bounds.height()

        val allText = mutableListOf<TextItem>()
        collectText(root, allText, screenHeight)

        return allText
            .sortedBy { it.y }
            .map { it.text.trim() }
            .filter { it.isNotBlank() && it.length > 2 }
            .distinct()
            .joinToString("\n")
            .take(3000)
    }

    private data class TextItem(val text: String, val y: Int)

    private fun collectText(
        node: AccessibilityNodeInfo?,
        results: MutableList<TextItem>,
        screenHeight: Int
    ) {
        if (node == null || results.size > 300) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val isVisible = bounds.top >= 0 && bounds.height() > 5
        val isInViewableArea = bounds.top < screenHeight * 0.85

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length > 2) {
            results.add(TextItem(text, bounds.top))
        }

        val contentDesc = node.contentDescription?.toString()?.trim()
        if (!contentDesc.isNullOrBlank() && contentDesc.length > 2) {
            results.add(TextItem(contentDesc, bounds.top))
        }

        if (isVisible && isInViewableArea) {
            for (i in 0 until node.childCount) {
                collectText(node.getChild(i), results, screenHeight)
            }
        }
    }
}

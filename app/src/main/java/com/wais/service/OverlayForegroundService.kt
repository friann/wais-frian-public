package com.wais.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wais.AppGraph
import com.wais.R
import com.wais.accessibility.AccessibilityTextStore
import com.wais.data.OverlayProgressStep
import com.wais.domain.model.AnalysisInput
import com.wais.domain.model.AnalysisResult
import com.wais.overlay.FloatingAnchor
import com.wais.overlay.FloatingButtonController
import com.wais.overlay.ResultOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayForegroundService : Service() {
    private val tag = "OverlayService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var floatingButtonController: FloatingButtonController
    private lateinit var resultOverlayController: ResultOverlayController
    private var analysisJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(tag, "onCreate: starting foreground service")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        resultOverlayController = ResultOverlayController(applicationContext)
        floatingButtonController = FloatingButtonController(
            context = applicationContext,
            onTap = { _: FloatingAnchor ->
                Log.d(tag, "floating button tapped - starting text analysis")
                resultOverlayController.showLoading()
                launchAnalysis { runAccessibilityAndAnalyze() }
            },
            onDoubleTap = { _: FloatingAnchor ->
                Log.d(tag, "floating button double-tapped - starting image check")
                startImageCheck()
            }
        )
        floatingButtonController.show()
    }

    private suspend fun runAccessibilityAndAnalyze() {
        try {
            val text = AccessibilityTextStore.latest()
            Log.d(tag, "=== BUTTON CLICKED - TEXT CAPTURED (${text.length} chars) ===")
            Log.d(tag, text.take(800))
            if (text.length > 800) Log.d(tag, "... [${text.length - 800} more]")
            Log.d(tag, "=== END CAPTURE ===")

            if (text.isBlank() || text.length < 50) {
                resultOverlayController.showNotice(
                    message = getString(R.string.image_hint),
                    actionLabel = getString(R.string.image_check_action),
                    onAction = { startImageCheck() }
                )
                return
            }

            val repo = AppGraph.provideLinkupRepository()
            val factCheckRepo = AppGraph.provideFactCheckRepository(applicationContext)
            
            resultOverlayController.updateLoadingState("Extracting claim...")
            
            val result = repo.analyzeWithProgress(text) { step ->
                when (step) {
                    OverlayProgressStep.EXTRACTING -> {
                        resultOverlayController.updateLoadingState("Extracting claim...")
                    }
                    OverlayProgressStep.SEARCHING -> {
                        resultOverlayController.updateLoadingState("Searching sources...")
                    }
                    OverlayProgressStep.DONE -> {}
                }
            }
            
            val analysisResult = AnalysisResult(
                extractedText = text,
                aiVerdict = result.verdict,
                aiSummary = result.summary,
                aiSources = result.sources,
                errors = result.error?.let { listOf(it) } ?: emptyList()
            )
            
            factCheckRepo.saveFromResult(analysisResult, text.take(200), "overlay")
            resultOverlayController.showResult(analysisResult)
        } catch (t: Throwable) {
            Log.e(tag, "runAccessibilityAndAnalyze failed", t)
            resultOverlayController.showResult(
                AnalysisResult(extractedText = null, errors = listOf(t.message ?: "Capture failed"))
            )
        }
    }

    private fun launchAnalysis(block: suspend () -> Unit) {
        analysisJob?.cancel()
        analysisJob = serviceScope.launch { block() }
    }

    /** Explicit, user-initiated image check. The first use asks for consent to send a screenshot. */
    private fun startImageCheck() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_IMAGE_DISCLOSURE_ACCEPTED, false)) {
            resultOverlayController.showNotice(
                message = getString(R.string.image_disclosure),
                actionLabel = getString(R.string.image_disclosure_accept),
                onAction = {
                    prefs.edit().putBoolean(KEY_IMAGE_DISCLOSURE_ACCEPTED, true).apply()
                    launchAnalysis { runImageAnalysis() }
                }
            )
            return
        }
        launchAnalysis { runImageAnalysis() }
    }

    private suspend fun runImageAnalysis() {
        try {
            val accessibility = PostAccessibilityService.instance
            if (accessibility == null) {
                resultOverlayController.showNotice(getString(R.string.image_needs_accessibility))
                return
            }

            // Hide Wais UI so the screenshot contains only the user's content.
            resultOverlayController.hide(immediate = true)
            floatingButtonController.setVisible(false)
            delay(SCREENSHOT_SETTLE_MS)
            val shot = try {
                accessibility.captureScreenshotJpeg()
            } finally {
                floatingButtonController.setVisible(true)
            }

            val jpegBase64 = when (shot) {
                is PostAccessibilityService.CaptureResult.Failure -> {
                    resultOverlayController.showNotice(shot.message)
                    return
                }
                is PostAccessibilityService.CaptureResult.Success -> shot.jpegBase64
            }

            resultOverlayController.showLoading("Extracting claim...")
            val repo = AppGraph.provideLinkupRepository()
            val result = repo.analyzeImageWithProgress(jpegBase64) { step ->
                when (step) {
                    OverlayProgressStep.EXTRACTING -> resultOverlayController.updateLoadingState("Extracting claim...")
                    OverlayProgressStep.SEARCHING -> resultOverlayController.updateLoadingState("Searching sources...")
                    OverlayProgressStep.DONE -> {}
                }
            }

            val claim = result.claim
            val analysisResult = AnalysisResult(
                extractedText = claim ?: "Image check",
                aiVerdict = result.verdict,
                aiSummary = result.summary,
                aiSources = result.sources,
                errors = result.error?.let { listOf(it) } ?: emptyList()
            )
            AppGraph.provideFactCheckRepository(applicationContext)
                .saveFromResult(analysisResult, (claim ?: "Image check").take(200), "overlay")
            resultOverlayController.showResult(analysisResult)
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.e(tag, "runImageAnalysis failed", t)
            resultOverlayController.showResult(
                AnalysisResult(extractedText = null, errors = listOf(t.message ?: "Image check failed"))
            )
        }
    }

    override fun onDestroy() {
        isRunning = false
        floatingButtonController.hide()
        resultOverlayController.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "wais_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "wais_prefs"
        private const val KEY_IMAGE_DISCLOSURE_ACCEPTED = "image_disclosure_accepted"
        private const val SCREENSHOT_SETTLE_MS = 250L
        @Volatile
        var isRunning: Boolean = false
    }
}

package com.wais.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.util.Log
import androidx.core.content.ContextCompat
import com.wais.R
import com.wais.domain.model.AiSource
import com.wais.domain.model.AnalysisResult
import com.wais.util.ThemeHelper
import com.wais.util.UrlOpener

class ResultOverlayController(
    private val context: Context
) {
    private val tag = "ResultOverlay"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private var isHiding = false
    private var isMinimized = false
    private var contentContainer: LinearLayout? = null
    private var minimizeButton: ImageButton? = null
    private var loadingText: TextView? = null
    private var loadingSubtext: TextView? = null
    private var currentResult: AnalysisResult? = null

    private val c get() = context

    private fun dark(): Boolean = ThemeHelper.isDarkMode(c)

    private val surface get() = if (dark()) 0xFF131313.toInt() else 0xFFFAFAFA.toInt()
    private val surfaceContainer get() = if (dark()) 0xFF201F1F.toInt() else 0xFFF0F0F0.toInt()
    private val surfaceContainerHigh get() = if (dark()) 0xFF2A2A2A.toInt() else 0xFFE8E8E8.toInt()
    private val surfaceContainerLowest get() = if (dark()) 0xFF0E0E0E.toInt() else 0xFFFFFFFF.toInt()
    private val onSurface get() = if (dark()) 0xFFE5E2E1.toInt() else 0xFF1A1A1A.toInt()
    private val onSurfaceVariant get() = if (dark()) 0xFFBBCABF.toInt() else 0xFF5C5C5C.toInt()
    private val outlineVariant get() = if (dark()) 0xFF3C4A42.toInt() else 0xFFC4C4C4.toInt()
    private val primary get() = 0xFF4EDEA3.toInt()
    private val primaryContainer get() = if (dark()) 0xFF10B981.toInt() else 0xFFA7F3D0.toInt()
    private val onPrimaryContainer get() = if (dark()) 0xFF00422B.toInt() else 0xFF00422B.toInt()
    private val onPrimary get() = if (dark()) 0xFF003824.toInt() else 0xFF003824.toInt()
    private val errorContainer get() = if (dark()) 0xFF93000A.toInt() else 0xFFFFDAD6.toInt()
    private val onErrorContainer get() = if (dark()) 0xFFFFDAD6.toInt() else 0xFF410002.toInt()
    private val secondaryContainer get() = if (dark()) 0xFF21523C.toInt() else 0xFFC8E6D4.toInt()
    private val onSecondaryContainer get() = if (dark()) 0xFF91C4A8.toInt() else 0xFF1A3D2B.toInt()
    private val surfaceHighest get() = if (dark()) 0xFF353534.toInt() else 0xFFE0E0E0.toInt()

    fun showLoading(state: String = "Analyzing content...") {
        currentResult = null
        showRaw(
            aiSummary = c.getString(R.string.loading),
            verdict = "ANALYZING",
            aiSources = emptyList(),
            errors = emptyList(),
            isLoading = true,
            legitimacyPercentage = null,
            loadingState = state
        )
    }

    /** Shows a plain message card, optionally with one action button (e.g. "Check image"). */
    fun showNotice(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        currentResult = null
        showRaw(
            aiSummary = message,
            verdict = "",
            aiSources = emptyList(),
            errors = emptyList(),
            isLoading = false,
            noticeText = message,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }

    fun updateLoadingState(state: String) {
        loadingText?.text = state
        loadingSubtext?.text = getLoadingSubtext(state)
    }

    private fun getLoadingSubtext(state: String): String {
        return when {
            state.contains("Fetching") -> "Retrieving page content"
            state.contains("Extracting") -> "Identifying main claim"
            state.contains("Searching") -> "Finding credible sources"
            state.contains("Verifying") -> "Cross-referencing facts"
            else -> "Please wait..."
        }
    }

    fun showResult(result: AnalysisResult) {
        Log.d(tag, "showResult aiVerdict=${result.aiVerdict} aiSources=${result.aiSources.size} errors=${result.errors.size}")
        currentResult = result

        val verdict = result.aiVerdict.uppercase().ifEmpty { "ANALYZED" }
        val summary = result.aiSummary ?: "No AI analysis available"

        showRaw(
            aiSummary = summary,
            verdict = verdict,
            aiSources = result.aiSources,
            errors = result.errors,
            isLoading = false,
            legitimacyPercentage = result.legitimacyPercentage
        )
    }

    private fun showRaw(
        aiSummary: String,
        verdict: String,
        aiSources: List<AiSource>,
        errors: List<String>,
        isLoading: Boolean,
        legitimacyPercentage: Int? = null,
        loadingState: String = "Analyzing content...",
        noticeText: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        hide(immediate = true)
        isMinimized = false
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_result_modal, null)

        val modalRoot = view.findViewById<LinearLayout>(R.id.modalRoot)
        val headerRow = view.findViewById<LinearLayout>(R.id.headerRow)
        val waisHeader = view.findViewById<ImageView>(R.id.waisHeader)
        val headerDivider = view.findViewById<View>(R.id.headerDivider)
        val verdictDot = view.findViewById<View>(R.id.verdictDot)
        val verdictBadge = view.findViewById<TextView>(R.id.verdictBadge)
        val aiSummaryText = view.findViewById<TextView>(R.id.aiSummaryText)
        val sourcesContainer = view.findViewById<LinearLayout>(R.id.aiSourcesContainer)
        val errorText = view.findViewById<TextView>(R.id.errorText)
        val closeButton = view.findViewById<ImageButton>(R.id.closeResultButton)
        contentContainer = view.findViewById(R.id.contentContainer)
        minimizeButton = view.findViewById(R.id.minimizeResultButton)
        val legitimacyContainer = view.findViewById<LinearLayout>(R.id.legitimacyContainer)
        val legitimacyBar = view.findViewById<ProgressBar>(R.id.legitimacyBar)
        val legitimacyPercent = view.findViewById<TextView>(R.id.legitimacyPercent)
        val legitimacyLabel = view.findViewById<TextView>(R.id.legitimacyLabel)
        val loadingContainer = view.findViewById<LinearLayout>(R.id.loadingContainer)
        val resultContent = view.findViewById<LinearLayout>(R.id.resultContent)
        loadingText = view.findViewById(R.id.loadingText)
        loadingSubtext = view.findViewById(R.id.loadingSubtext)
        val shareDivider = view.findViewById<View>(R.id.shareDivider)
        val shareButton = view.findViewById<ImageButton>(R.id.shareResultButton)

        applyModalBackground(modalRoot)
        headerRow.setBackgroundColor(surfaceContainer)
        headerDivider.setBackgroundColor(outlineVariant)
        verdictBadge.setTextColor(onSurface)

        applyIconButtonBg(closeButton)
        applyIconButtonBg(minimizeButton!!)

        shareDivider.setBackgroundColor(outlineVariant)
        applyShareButtonBg(shareButton)
        shareButton.setColorFilter(onPrimary)

        shareButton.setOnClickListener {
            Log.d(tag, "Share button clicked")
            toggleMinimize(view)
            shareResult()
        }

        val noticeContainer = view.findViewById<LinearLayout>(R.id.noticeContainer)
        val noticeTextView = view.findViewById<TextView>(R.id.noticeText)
        val noticeActionButton = view.findViewById<Button>(R.id.noticeActionButton)

        if (noticeText != null) {
            loadingContainer.visibility = View.GONE
            resultContent.visibility = View.GONE
            noticeContainer.visibility = View.VISIBLE
            noticeTextView.text = noticeText
            noticeTextView.setTextColor(onSurface)
            if (actionLabel != null && onAction != null) {
                noticeActionButton.visibility = View.VISIBLE
                noticeActionButton.text = actionLabel
                noticeActionButton.setTextColor(onPrimary)
                applyShareButtonBg(noticeActionButton)
                noticeActionButton.setOnClickListener { onAction() }
            } else {
                noticeActionButton.visibility = View.GONE
            }
        } else if (isLoading) {
            loadingContainer.visibility = View.VISIBLE
            resultContent.visibility = View.GONE
            shareDivider.visibility = View.GONE
            shareButton.visibility = View.GONE
            loadingText?.text = loadingState
            loadingText?.setTextColor(onSurfaceVariant)
            loadingSubtext?.text = getLoadingSubtext(loadingState)
            loadingSubtext?.setTextColor(onSurfaceVariant)
        } else {
            loadingContainer.visibility = View.GONE
            resultContent.visibility = View.VISIBLE
            shareDivider.visibility = View.VISIBLE
            shareButton.visibility = View.VISIBLE

            aiSummaryText.text = aiSummary
            aiSummaryText.setTextColor(onSurface)

            val (dotColor, badgeBg, badgeTextColor) = when {
                verdict.equals("TRUE", ignoreCase = true) -> Triple(primary, primaryContainer, onPrimaryContainer)
                verdict.equals("FALSE", ignoreCase = true) -> Triple(0xFFFFB4AB.toInt(), errorContainer, onErrorContainer)
                verdict.equals("MISLEADING", ignoreCase = true) -> Triple(0xFF9ED2B5.toInt(), secondaryContainer, onSecondaryContainer)
                else -> Triple(onSurfaceVariant, surfaceContainerHigh, onSurfaceVariant)
            }

            applyDotColor(verdictDot, dotColor)
            applyBadgeBackground(verdictBadge, badgeBg)
            verdictBadge.setTextColor(badgeTextColor)
            verdictBadge.text = verdict

            if (legitimacyPercentage != null) {
                legitimacyContainer.visibility = View.VISIBLE
                legitimacyBar.progress = legitimacyPercentage
                legitimacyPercent.text = "${legitimacyPercentage}%"
            } else {
                legitimacyContainer.visibility = View.GONE
            }
        }

        legitimacyLabel.setTextColor(onSurfaceVariant)
        legitimacyPercent.setTextColor(primary)

        sourcesContainer.removeAllViews()
        aiSources.take(3).forEach { sourceItem ->
            val linkView = LayoutInflater.from(context).inflate(R.layout.item_source_link, sourcesContainer, false) as TextView
            linkView.text = sourceItem.url
            linkView.setOnClickListener {
                Log.d(tag, "Source clicked: ${sourceItem.url}")
                UrlOpener.open(c, sourceItem.url)
            }
            linkView.isClickable = true
            linkView.isFocusable = true
            sourcesContainer.addView(linkView)
        }

        view.post {
            val displayMetrics = c.resources.displayMetrics
            val maxWidth = (displayMetrics.widthPixels * 0.9f).toInt()
            val params = view.layoutParams as ViewGroup.LayoutParams
            params.width = maxWidth
            view.layoutParams = params
        }

        view.alpha = 0f
        view.translationY = -20f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()

        for (i in 0 until sourcesContainer.childCount) {
            val child = sourcesContainer.getChildAt(i)
            child.alpha = 0f
            child.translationY = 10f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(100L + (i * 60L))
                .setDuration(200)
                .start()
        }

        errorText.text = errors.joinToString("\n")
        errorText.visibility = if (errors.isEmpty()) View.GONE else View.VISIBLE

        closeButton.setOnClickListener {
            hide(immediate = false)
        }

        minimizeButton?.setOnClickListener {
            toggleMinimize(view)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = 110
        }

        windowManager.addView(view, params)
        rootView = view
    }

    private fun applyModalBackground(view: View) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = 4f * c.resources.displayMetrics.density
        d.setColor(surfaceContainer)
        d.setStroke((1f * c.resources.displayMetrics.density).toInt(), outlineVariant)
        view.background = d
    }

    private fun applyIconButtonBg(btn: ImageButton) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(surfaceContainerLowest)
        d.setStroke((1f * c.resources.displayMetrics.density).toInt(), outlineVariant)
        btn.background = d
        btn.setColorFilter(onSurfaceVariant)
    }

    private fun applyDotColor(view: View, color: Int) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        view.background = d
    }

    private fun applyBadgeBackground(view: TextView, color: Int) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = 8f * c.resources.displayMetrics.density
        d.setColor(color)
        view.background = d
    }

    private fun applyShareButtonBg(view: View) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = 8f * c.resources.displayMetrics.density
        d.setColor(primary)
        view.background = d
    }

    private fun applyLegitimacyBarBg(progressBar: ProgressBar) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = 3f * c.resources.displayMetrics.density
        d.setColor(surfaceHighest)
        progressBar.progressDrawable = createLegitimacyProgressDrawable()
    }

    private fun createLegitimacyProgressDrawable(): android.graphics.drawable.LayerDrawable {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 3f * c.resources.displayMetrics.density
        bg.setColor(surfaceHighest)

        val fg = GradientDrawable()
        fg.shape = GradientDrawable.RECTANGLE
        fg.cornerRadius = 3f * c.resources.displayMetrics.density
        fg.setColor(primaryContainer)

        val layer = android.graphics.drawable.LayerDrawable(arrayOf(bg, fg))
        layer.setId(0, android.R.id.background)
        layer.setId(1, android.R.id.progress)
        return layer
    }

    private fun toggleMinimize(view: View) {
        isMinimized = !isMinimized
        if (isMinimized) {
            minimizeContent(view)
        } else {
            contentContainer?.visibility = View.VISIBLE
            minimizeButton?.setImageResource(R.drawable.ic_minimize)
            applyIconButtonBg(minimizeButton!!)
        }
    }

    private fun minimizeResult(view: View) {
        isMinimized = true
        minimizeContent(view)
    }

    private fun minimizeContent(view: View) {
        contentContainer?.visibility = View.GONE
        minimizeButton?.setImageResource(R.drawable.ic_maximize)
        minimizeButton?.clearColorFilter()
    }

    private fun shareResult() {
        val result = currentResult ?: return
        val shareText = buildShareText(result)

        val clipboard = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WAIS Fact Check", shareText)
        clipboard.setPrimaryClip(clip)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "WAIS Fact Check")
        }

        val chooser = Intent.createChooser(shareIntent, "Share fact check via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(chooser)
    }

    private fun buildShareText(result: AnalysisResult): String {
        val verdict = result.aiVerdict.uppercase().ifEmpty { "UNVERIFIED" }
        val legit = result.legitimacyPercentage?.let { "${it}%" } ?: "--"
        val summary = result.aiSummary ?: "No analysis available."
        val sources = result.aiSources.take(3)

        return buildString {
            appendLine("WAIS FACT CHECK")
            appendLine("━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("VERDICT: $verdict")
            appendLine("LEGITIMACY: $legit")
            appendLine()
            appendLine("SUMMARY")
            appendLine(summary)
            if (sources.isNotEmpty()) {
                appendLine()
                appendLine("SOURCES")
                sources.forEach { src ->
                    appendLine("• ${src.url}")
                }
            }
            appendLine()
            appendLine("Checked with WAIS — Realtime Fact Checker & AI Detection")
        }
    }

    fun hide(immediate: Boolean = false) {
        val view = rootView ?: return
        if (isHiding) return

        if (immediate) {
            isHiding = false
            isMinimized = false
            contentContainer = null
            minimizeButton = null
            windowManager.removeView(view)
            rootView = null
            return
        }

        isHiding = true
        view.animate()
            .alpha(0f)
            .translationY(-20f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (rootView === view) {
                        windowManager.removeView(view)
                        rootView = null
                        isMinimized = false
                        contentContainer = null
                        minimizeButton = null
                    }
                    isHiding = false
                }
            })
            .start()
    }
}

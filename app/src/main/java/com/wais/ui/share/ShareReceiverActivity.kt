package com.wais.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.appcompat.app.AppCompatActivity
import com.wais.AppGraph
import com.wais.domain.model.AnalysisResult
import com.wais.overlay.ResultOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

import com.wais.data.ShareProgressStep

class ShareReceiverActivity : AppCompatActivity() {
    private val tag = "ShareReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resultOverlayController = ResultOverlayController(applicationContext)
        resultOverlayController.showLoading("Initializing...")

        val sendIntent = intent
        val type = sendIntent.type.orEmpty()
        Log.d(tag, "onCreate action=${sendIntent.action} type=$type")

        scope.launch {
            when {
                sendIntent.action == Intent.ACTION_SEND && type.startsWith("text/") -> {
                    val text = sendIntent.getStringExtra(Intent.EXTRA_TEXT)
                    val sharedUrl = text?.takeIf { it.startsWith("http") }
                    
                    if (sharedUrl != null) {
                        Log.d(tag, "URL shared: $sharedUrl")
                        openSharedLinkInApp(sharedUrl)
                        analyzeUrl(sharedUrl, resultOverlayController)
                    } else {
                        Log.d(tag, "Text shared (no URL): ${text?.take(100)}")
                        resultOverlayController.showResult(
                            AnalysisResult(
                                extractedText = text,
                                errors = listOf("No URL found in shared text")
                            )
                        )
                        finish()
                    }
                }

                sendIntent.action == Intent.ACTION_SEND && type.startsWith("image/") -> {
                    val imageUri = sendIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    val urlFromExtras = sendIntent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.startsWith("http") }
                    
                    if (urlFromExtras != null) {
                        openSharedLinkInApp(urlFromExtras)
                        analyzeUrl(urlFromExtras, resultOverlayController)
                    } else {
                        resultOverlayController.showResult(
                            AnalysisResult(
                                extractedText = null,
                                errors = listOf("No URL associated with shared image")
                            )
                        )
                        finish()
                    }
                }

                else -> {
                    resultOverlayController.showResult(
                        AnalysisResult(
                            extractedText = null,
                            errors = listOf("Unsupported share type")
                        )
                    )
                    finish()
                }
            }
        }
    }

    private suspend fun analyzeUrl(url: String, resultOverlayController: ResultOverlayController) {
        try {
            val shareRepo = AppGraph.provideShareRepository()
            
            resultOverlayController.updateLoadingState("Fetching content...")
            Log.d(tag, "Fetching content...")
            
            val result = shareRepo.analyzeWithProgress(url) { step ->
                when (step) {
                    ShareProgressStep.FETCHING -> {
                        resultOverlayController.updateLoadingState("Fetching content...")
                        Log.d(tag, "Step: Fetching")
                    }
                    ShareProgressStep.EXTRACTING -> {
                        resultOverlayController.updateLoadingState("Extracting claim...")
                        Log.d(tag, "Step: Extracting")
                    }
                    ShareProgressStep.SEARCHING -> {
                        resultOverlayController.updateLoadingState("Searching sources...")
                        Log.d(tag, "Step: Searching")
                    }
                    ShareProgressStep.DONE -> {
                        Log.d(tag, "Step: Done")
                    }
                }
            }
            
            Log.d(tag, "Analysis complete: verdict=${result.verdict}")
            val analysisResult = AnalysisResult(
                extractedText = url,
                aiVerdict = result.verdict,
                aiSummary = result.summary,
                aiSources = result.sources,
                errors = result.error?.let { listOf(it) } ?: emptyList()
            )
            AppGraph.provideFactCheckRepository(applicationContext).saveFromResult(analysisResult, url, "share")
            resultOverlayController.showResult(analysisResult)
        } catch (e: Exception) {
            Log.e(tag, "Analysis failed", e)
            resultOverlayController.showResult(
                AnalysisResult(
                    extractedText = url,
                    errors = listOf(e.message ?: "Analysis failed")
                )
            )
        } finally {
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openSharedLinkInApp(url: String) {
        runCatching {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
        }.onFailure {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}

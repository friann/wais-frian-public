package com.wais.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wais.R
import com.wais.AppGraph
import com.wais.data.local.FactCheckEntity
import com.wais.service.OverlayForegroundService
import com.wais.ui.theme.UntitledTheme
import com.wais.util.PermissionHelper
import com.wais.util.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("untitled_prefs", MODE_PRIVATE) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applySavedTheme(this)
        val isDark = ThemeHelper.isDarkMode(this)
        setContent {
            UntitledTheme(darkTheme = isDark) {
                MainScreen(
                    isDarkTheme = isDark,
                    onThemeToggle = { dark ->
                        ThemeHelper.setDarkMode(this, dark)
                        recreate()
                    },
                    onOverlayClick = { PermissionHelper.openOverlayPermission(this) },
                    onAccessibilityClick = {
                        PermissionHelper.openAccessibilitySettings(this)
                        Toast.makeText(this, getString(R.string.accessibility_instruction_toast), Toast.LENGTH_SHORT).show()
                    },
                    onToggleService = {
                        if (OverlayForegroundService.isRunning) {
                            stopService(Intent(this, OverlayForegroundService::class.java))
                            Toast.makeText(this, getString(R.string.service_stopped), Toast.LENGTH_SHORT).show()
                        } else {
                            if (!PermissionHelper.canDrawOverlays(this)) {
                                Toast.makeText(this, getString(R.string.overlay_permission_needed), Toast.LENGTH_SHORT).show()
                                return@MainScreen
                            }
                            requestNotificationPermissionIfNeeded()
                            ContextCompat.startForegroundService(this, Intent(this, OverlayForegroundService::class.java))
                            Toast.makeText(this, getString(R.string.service_started), Toast.LENGTH_SHORT).show()
                        }
                    },
                    statusProvider = {
                        val overlayReady = PermissionHelper.canDrawOverlays(this)
                        val accessibilityReady = PermissionHelper.isAccessibilityServiceEnabled(this)
                        val allReady = overlayReady && accessibilityReady

                        val wasSetupCompleted = prefs.getBoolean(KEY_SETUP_DONE, false)
                        if (allReady && !wasSetupCompleted) {
                            prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply()
                            Toast.makeText(this, getString(R.string.setup_complete_toast), Toast.LENGTH_SHORT).show()
                        }

                        val status = if (allReady) {
                            getString(R.string.ready_all_permissions)
                        } else if (overlayReady) {
                            getString(R.string.accessibility_permission_needed)
                        } else {
                            getString(R.string.overlay_permission_needed)
                        }

                        val hint = when {
                            allReady -> getString(R.string.setup_hint_ready)
                            !overlayReady && !accessibilityReady -> getString(R.string.setup_hint_both_missing)
                            !overlayReady -> getString(R.string.setup_hint_overlay_missing)
                            else -> getString(R.string.setup_hint_accessibility_missing)
                        }

                        status to hint
                    },
                    getServiceRunning = { OverlayForegroundService.isRunning }
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val KEY_SETUP_DONE = "setup_done"
    }
}

@Composable
private fun MainScreen(
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onOverlayClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    onToggleService: () -> Unit,
    statusProvider: () -> Pair<String, String>,
    getServiceRunning: () -> Boolean
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val surfaceColor = if (isDarkTheme) Color(0xFF131313) else Color(0xFFFAFAFA)
    val onSurfaceColor = if (isDarkTheme) Color(0xFFE5E2E1) else Color(0xFF1A1A1A)
    val onSurfaceVariantColor = if (isDarkTheme) Color(0xFFBBCABF) else Color(0xFF5C5C5C)
    val primaryColor = Color(0xFF4EDEA3)
    
    Scaffold(
        containerColor = surfaceColor,
        bottomBar = {
            BottomNavBar(
                selectedIndex = selectedTab,
                onItemSelected = { selectedTab = it },
                isDarkTheme = isDarkTheme,
                surfaceColor = surfaceColor,
                primaryColor = primaryColor,
                onSurfaceVariantColor = onSurfaceVariantColor
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> HomeTab(
                    onToggleService = onToggleService,
                    getServiceRunning = getServiceRunning,
                    permissionProvider = statusProvider,
                    isDarkTheme = isDarkTheme,
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor,
                    primaryColor = primaryColor
                )
                1 -> HistoryTab(isDarkTheme, surfaceColor, onSurfaceColor, onSurfaceVariantColor, primaryColor)
                2 -> SettingsTab(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle,
                    onOverlayClick = onOverlayClick,
                    onAccessibilityClick = onAccessibilityClick,
                    statusProvider = statusProvider,
                    getServiceRunning = getServiceRunning
                )
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    isDarkTheme: Boolean,
    surfaceColor: Color,
    primaryColor: Color,
    onSurfaceVariantColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(R.drawable.nav_home, selectedIndex == 0, primaryColor, onSurfaceVariantColor) { onItemSelected(0) }
        NavItem(R.drawable.nav_history, selectedIndex == 1, primaryColor, onSurfaceVariantColor) { onItemSelected(1) }
        NavItem(R.drawable.nav_settings, selectedIndex == 2, primaryColor, onSurfaceVariantColor) { onItemSelected(2) }
    }
}

@Composable
private fun NavItem(
    iconRes: Int,
    isSelected: Boolean,
    primaryColor: Color,
    onSurfaceVariantColor: Color,
    onClick: () -> Unit
) {
    val tint = if (isSelected) primaryColor else onSurfaceVariantColor.copy(alpha = 0.5f)
    
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 32.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun HistoryTab(
    isDarkTheme: Boolean,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val factCheckRepo = remember { AppGraph.provideFactCheckRepository(context) }
    var factChecks by remember { mutableStateOf<List<FactCheckEntity>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<FactCheckEntity?>(null) }

    val cardBgColor = if (isDarkTheme) Color(0xFF201F1F) else Color(0xFFF0F0F0)
    val cardBorderColor = if (isDarkTheme) Color(0xFF3C4A42) else Color(0xFFC4C4C4)

    LaunchedEffect(Unit) {
        factCheckRepo.getAllFactChecks().collect { checks ->
            factChecks = checks
        }
    }

    if (selectedItem != null) {
        ArchiveDetailDialog(
            item = selectedItem!!,
            isDarkTheme = isDarkTheme,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            onSurfaceVariantColor = onSurfaceVariantColor,
            primaryColor = primaryColor,
            cardBgColor = cardBgColor,
            cardBorderColor = cardBorderColor,
            onDismiss = { selectedItem = null },
            onDelete = {
                scope.launch(Dispatchers.IO) {
                    factCheckRepo.delete(selectedItem!!.id)
                }
                selectedItem = null
            },
            onShare = { text ->
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "WAIS Fact Check")
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share fact check via"))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Text(
            text = "ARCHIVE",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your fact check history",
            fontSize = 12.sp,
            color = onSurfaceVariantColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (factChecks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No fact checks yet",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceVariantColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Start scanning posts to build your archive",
                    fontSize = 12.sp,
                    color = onSurfaceVariantColor.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(factChecks.size) { index ->
                    val item = factChecks[index]
                    ArchiveCard(
                        verdict = item.verdict,
                        summary = item.summary,
                        source = item.source,
                        timestamp = item.timestamp,
                        legitimacyPercent = item.legitimacyPercent,
                        isDarkTheme = isDarkTheme,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVariantColor = onSurfaceVariantColor,
                        primaryColor = primaryColor,
                        onClick = { selectedItem = item },
                        onDelete = {
                            scope.launch(Dispatchers.IO) {
                                factCheckRepo.delete(item.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveCard(
    verdict: String,
    summary: String,
    source: String,
    timestamp: Long,
    legitimacyPercent: Int?,
    isDarkTheme: Boolean,
    cardBgColor: Color,
    cardBorderColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val verdictColor = when {
        verdict.equals("TRUE", ignoreCase = true) -> primaryColor
        verdict.equals("FALSE", ignoreCase = true) -> Color(0xFFFFB4AB.toInt())
        verdict.equals("MISLEADING", ignoreCase = true) -> Color(0xFF9ED2B5.toInt())
        else -> onSurfaceVariantColor
    }

    val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))

    val sourceLabel = if (source == "share") "Shared link" else "Screen capture"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = verdict,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (legitimacyPercent != null) {
                        Text(
                            text = "${legitimacyPercent}%",
                            fontSize = 11.sp,
                            color = primaryColor
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_small),
                        contentDescription = "Delete",
                        tint = onSurfaceVariantColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                fontSize = 12.sp,
                color = onSurfaceColor,
                maxLines = 3,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sourceLabel,
                    fontSize = 10.sp,
                    color = onSurfaceVariantColor.copy(alpha = 0.6f)
                )
                Text(
                    text = date,
                    fontSize = 10.sp,
                    color = onSurfaceVariantColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ArchiveDetailDialog(
    item: FactCheckEntity,
    isDarkTheme: Boolean,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onShare: (String) -> Unit
) {
    val verdictColor = when {
        item.verdict.equals("TRUE", ignoreCase = true) -> primaryColor
        item.verdict.equals("FALSE", ignoreCase = true) -> Color(0xFFFFB4AB.toInt())
        item.verdict.equals("MISLEADING", ignoreCase = true) -> Color(0xFF9ED2B5.toInt())
        else -> onSurfaceVariantColor
    }

    val date = java.text.SimpleDateFormat("MMMM dd, yyyy  HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(item.timestamp))

    val sourceLabel = if (item.source == "share") "Shared link" else "Screen capture"

    val shareText = buildString {
        appendLine("WAIS FACT CHECK")
        appendLine("━━━━━━━━━━━━━━━")
        appendLine()
        appendLine("VERDICT: ${item.verdict.uppercase()}")
        if (item.legitimacyPercent != null) appendLine("LEGITIMACY: ${item.legitimacyPercent}%")
        appendLine()
        appendLine("CLAIM")
        appendLine(item.claim)
        appendLine()
        appendLine("ANALYSIS")
        appendLine(item.summary)
        if (item.sourceLinks.isNotEmpty()) {
            appendLine()
            appendLine("SOURCES")
            item.sourceLinks.forEach { appendLine("• $it") }
        }
        appendLine()
        appendLine("Checked with WAIS — Realtime Fact Checker & AI Detection")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBgColor,
        titleContentColor = onSurfaceColor,
        textContentColor = onSurfaceColor,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "FACT CHECK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_small),
                        contentDescription = "Close",
                        tint = onSurfaceVariantColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(verdictColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.verdict.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor
                    )
                    if (item.legitimacyPercent != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${item.legitimacyPercent}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "CLAIM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.claim,
                    fontSize = 13.sp,
                    color = onSurfaceColor,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ANALYSIS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.summary,
                    fontSize = 13.sp,
                    color = onSurfaceColor,
                    lineHeight = 18.sp
                )

                if (item.sourceLinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "SOURCES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceVariantColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    item.sourceLinks.forEach { link ->
                        Text(
                            text = "• $link",
                            fontSize = 12.sp,
                            color = primaryColor,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                if (item.errors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = item.errors.joinToString("\n"),
                        fontSize = 12.sp,
                        color = Color(0xFFFFB4AB.toInt())
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = cardBorderColor)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = sourceLabel,
                            fontSize = 10.sp,
                            color = onSurfaceVariantColor.copy(alpha = 0.6f)
                        )
                        Text(
                            text = date,
                            fontSize = 10.sp,
                            color = onSurfaceVariantColor.copy(alpha = 0.6f)
                        )
                    }
                    Row {
                        Text(
                            text = "Share",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryColor,
                            modifier = Modifier.clickable { onShare(shareText) }
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = "Delete",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFB4AB.toInt()),
                            modifier = Modifier.clickable { onDelete() }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun HomeTab(
    onToggleService: () -> Unit,
    getServiceRunning: () -> Boolean,
    permissionProvider: () -> Pair<String, String>,
    isDarkTheme: Boolean,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color
) {
    var isRunning by remember { mutableStateOf(getServiceRunning()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val currentState = getServiceRunning()
            if (currentState != isRunning) {
                isRunning = currentState
            }
        }
    }

    val (status, _) = permissionProvider()
    val hasAllPermissions = status.contains("Ready")

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.header),
            contentDescription = "Wais",
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            alignment = Alignment.Center
        )

        Text(
            text = "Realtime Fact Checker & Analyzer",
            fontSize = 12.sp,
            color = onSurfaceVariantColor,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isRunning) Color(0xFF93000A).copy(alpha = glowAlpha) else Color(0xFF10B981).copy(alpha = glowAlpha),
                            if (isRunning) Color(0xFF93000A) else Color(0xFF10B981)
                        )
                    )
                )
                .clickable { 
                    onToggleService()
                    isRunning = !isRunning
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = if (isRunning) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = if (isRunning) "Stop" else "Start",
                modifier = Modifier.size(72.dp),
                colorFilter = ColorFilter.tint(Color(0xFFE5E2E1))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isRunning) "OVERLAY ACTIVE" else "TAP TO START",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRunning) primaryColor else onSurfaceVariantColor,
            textAlign = TextAlign.Center,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) primaryColor else onSurfaceVariantColor)
            )
            Text(
                text = if (isRunning) "Service Running" else "Service Stopped",
                fontSize = 11.sp,
                color = onSurfaceVariantColor,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsTab(
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onOverlayClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    statusProvider: () -> Pair<String, String>,
    getServiceRunning: () -> Boolean
) {
    val (status, hint) = statusProvider()

    val surfaceColor = if (isDarkTheme) Color(0xFF131313) else Color(0xFFFAFAFA)
    val onSurfaceColor = if (isDarkTheme) Color(0xFFE5E2E1) else Color(0xFF1A1A1A)
    val onSurfaceVariantColor = if (isDarkTheme) Color(0xFFBBCABF) else Color(0xFF5C5C5C)
    val cardBgColor = if (isDarkTheme) Color(0xFF201F1F) else Color(0xFFF0F0F0)
    val cardBorderColor = if (isDarkTheme) Color(0xFF3C4A42) else Color(0xFFC4C4C4)
    val primaryColor = Color(0xFF4EDEA3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Text(
            text = "SETTINGS",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configure your experience",
            fontSize = 12.sp,
            color = onSurfaceVariantColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "APPEARANCE",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = onSurfaceVariantColor,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dark Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceColor
                    )
                    Text(
                        text = if (isDarkTheme) "Currently using dark theme" else "Currently using light theme",
                        fontSize = 11.sp,
                        color = onSurfaceVariantColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onThemeToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = primaryColor,
                        checkedTrackColor = primaryColor.copy(alpha = 0.4f),
                        uncheckedThumbColor = onSurfaceVariantColor,
                        uncheckedTrackColor = cardBorderColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "PERMISSIONS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = onSurfaceVariantColor,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingCard(
            title = "Display Over Apps",
            description = "Required to show floating button",
            status = "Required",
            isGranted = false,
            isDarkTheme = isDarkTheme,
            cardBgColor = cardBgColor,
            cardBorderColor = cardBorderColor,
            onSurfaceColor = onSurfaceColor,
            onSurfaceVariantColor = onSurfaceVariantColor,
            primaryColor = primaryColor,
            onClick = onOverlayClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingCard(
            title = "Accessibility Service",
            description = "Required to read screen content",
            status = "Required",
            isGranted = false,
            isDarkTheme = isDarkTheme,
            cardBgColor = cardBgColor,
            cardBorderColor = cardBorderColor,
            onSurfaceColor = onSurfaceColor,
            onSurfaceVariantColor = onSurfaceVariantColor,
            primaryColor = primaryColor,
            onClick = onAccessibilityClick
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "STATUS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = onSurfaceVariantColor,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurfaceColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = onSurfaceVariantColor
                )
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    description: String,
    status: String,
    isGranted: Boolean,
    isDarkTheme: Boolean,
    cardBgColor: Color,
    cardBorderColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, cardBorderColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceColor
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = onSurfaceVariantColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = status,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Text(
                    text = "Tap to grant",
                    fontSize = 10.sp,
                    color = onSurfaceVariantColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

package dev.shortblocker.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PsychologyAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shortblocker.app.MainActivity
import dev.shortblocker.app.R
import dev.shortblocker.app.data.AppState
import dev.shortblocker.app.data.DailyStats
import dev.shortblocker.app.data.DemoPreset
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.SessionLog
import dev.shortblocker.app.data.UserAction
import dev.shortblocker.app.data.WarningLevel
import dev.shortblocker.app.domain.DetectionDecision
import java.time.ZoneId

private enum class AppTab(val route: String, val title: String, val icon: @Composable () -> Unit) {
    DASHBOARD("dashboard", "Dashboard", { Icon(Icons.Outlined.Dashboard, contentDescription = null) }),
    MONITOR("monitor", "Monitor", { Icon(Icons.Outlined.Visibility, contentDescription = null) }),
    LAB("lab", "Lab", { Icon(Icons.Outlined.PsychologyAlt, contentDescription = null) }),
    SPEC("spec", "Spec", { Icon(Icons.Outlined.Analytics, contentDescription = null) });

    companion object {
        fun fromRoute(route: String?): AppTab =
            entries.firstOrNull { it.route == route } ?: DASHBOARD
    }
}

private val architectureLayers = listOf(
    "Context Detection Layer" to listOf("前景アプリ", "使用時間", "時刻", "セッション継続時間"),
    "UI / Interaction Sensing Layer" to listOf("Accessibility Service", "画面テキスト", "View階層", "スクロール / スワイプイベント"),
    "Short-Video State Estimation Layer" to listOf("ルールベース判定", "特徴量スコアリング", "将来は軽量ML分類器"),
    "Intervention Layer" to listOf("キャラクター通知", "ポップアップ介入", "行動選択", "利用ログ保存"),
)

private val scopeColumns = listOf(
    "Must Have" to listOf(
        "Androidアプリの基本構造",
        "Accessibility Service ベースの UI 検知",
        "1〜3種類の短尺動画UIへの対応",
        "キャラクター通知",
        "行動選択肢",
        "ログ保存",
        "ダッシュボード表示",
    ),
    "Nice to Have" to listOf(
        "キャラクターの表情差分",
        "状況依存セリフ",
        "軽量ML分類器",
        "無視が続いた時の反応変化",
        "守れた時間の推定",
    ),
    "Out of Scope" to listOf(
        "高精度CVモデルの常時解析",
        "サーバー前提の個人最適化",
        "Live2Dや高度なアニメーション",
        "iOS完全対応",
        "複雑なクラウド同期",
    ),
)

private val roadmapPhases = listOf(
    "Phase 1" to listOf("Androidプロジェクト初期化", "Accessibility Service セットアップ", "対象アプリの前景検知", "基本通知"),
    "Phase 2" to listOf("UI特徴抽出", "short-video score 実装", "キャラ通知文の出し分け", "ログ保存"),
    "Phase 3" to listOf("ダッシュボードUI", "デモ調整", "キャラ表情差分", "発表資料作成"),
)

private val evaluationPlan = listOf(
    "Functional Evaluation" to listOf("対象UIを正しく検知できるか", "通知が所定条件で発火するか", "ログが正しく保存されるか"),
    "UX Evaluation" to listOf("キャラクター通知が見たくなるか", "無機質な警告より止まりやすいか", "うるさすぎないか"),
    "Demo Success Criteria" to listOf("短尺動画視聴状態の検知が見せられる", "キャラ通知の面白さが伝わる", "ダッシュボードで価値が可視化される"),
)

private val risks = listOf(
    "Technical Risks" to listOf("アプリごとに UI 構造が違い汎化が難しい", "Accessibility 実装は端末差と癖がある", "バックグラウンド権限説明が必要"),
    "UX Risks" to listOf("通知が多すぎると鬱陶しい", "キャラクターが飽きられる", "誤検知で通常利用まで邪魔する"),
    "Mitigation" to listOf("閾値を高めに設定する", "介入頻度を制限する", "通知強度を出し分ける", "MVPでは対象サービスを限定する"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortblockerApp(
    viewModel: MainViewModel,
    requestedTab: String?,
) {
    val context = LocalContext.current
    val state by viewModel.appState.collectAsStateWithLifecycle()
    val preset by viewModel.activePreset.collectAsStateWithLifecycle()
    val demoDecision by viewModel.demoDecision.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshRuntimeState(context)
    }

    LaunchedEffect(requestedTab) {
        currentTab = AppTab.fromRoute(requestedTab)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshRuntimeState(context)
    }

    if (state.pendingIntervention != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissIntervention,
            confirmButton = {
                Button(onClick = { viewModel.applyUserAction(UserAction.STOP) }) {
                    Text("今やめる")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.applyUserAction(UserAction.EXTEND) }) {
                        Text("あと1分だけ")
                    }
                    TextButton(onClick = { viewModel.applyUserAction(UserAction.IGNORE) }) {
                        Text("無視する")
                    }
                }
            },
            title = { Text("${state.pendingIntervention?.appName} / score ${state.pendingIntervention?.score}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.pendingIntervention?.dialogue.orEmpty())
                    Text(
                        text = "source ${state.pendingIntervention?.source} / ${state.pendingIntervention?.warningLevel?.label.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Shortblocker")
                        Text(
                            text = "Kotlin + Android migration",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                for (tab in AppTab.entries) {
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = tab.icon,
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (currentTab) {
                AppTab.DASHBOARD -> DashboardScreen(state = state)
                AppTab.MONITOR -> MonitorScreen(
                    state = state,
                    onRefresh = { viewModel.refreshRuntimeState(context) },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.refreshRuntimeState(context)
                        }
                    },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenUsageSettings = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onOpenNotificationSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            },
                        )
                    },
                    onThresholdChange = viewModel::updateThreshold,
                    onCooldownChange = viewModel::updateCooldownMinutes,
                    onDailyGoalChange = viewModel::updateDailyGoalMinutes,
                    onToggleAlerts = viewModel::toggleAlerts,
                    onToggleService = viewModel::toggleSupportedService,
                    onResetCooldown = viewModel::resetCooldown,
                )
                AppTab.LAB -> LabScreen(
                    state = state,
                    preset = preset,
                    decision = demoDecision,
                    onSelectPreset = viewModel::setPreset,
                    onAdvanceSwipe = viewModel::advanceSwipe,
                    onTrigger = viewModel::triggerDemo,
                )
                AppTab.SPEC -> SpecScreen()
            }
        }
    }
}

@Composable
private fun DashboardScreen(state: AppState) {
    val stats = remember(state.sessionLogs, state.settings.dailyGoalMinutes) { state.dailyStats() }
    val zoneId = remember { ZoneId.systemDefault() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(state = state, stats = stats)
        }
        item {
            SummaryRow(stats = stats)
        }
        item {
            CharacterStateCard(state = state)
        }
        item {
            SectionCard(
                title = "Recent SessionLog",
                subtitle = "stop / extend / ignore の選択履歴",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (log in state.sessionLogs.take(8)) {
                        LogRow(log = log, zoneId = zoneId)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(state: AppState, stats: DailyStats) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "UI体験を検知して止める Android アプリ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Accessibility Service / Usage Stats / Notification アクションで、短尺動画への再突入を Kotlin 側で完結させます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusBadge(state.liveMonitor.statusLabel, warningColor(state.liveMonitor.warningLevel))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    painter = painterResource(R.drawable.brainrot),
                    contentDescription = null,
                    modifier = Modifier
                        .size(128.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(title = "警告回数", value = stats.warningCount.toString(), modifier = Modifier.weight(1f))
                MetricCard(title = "推定セーブ時間", value = "${stats.estimatedSavedMinutes} min", modifier = Modifier.weight(1f))
                MetricCard(title = "危険時間帯", value = stats.mostRiskyTimeBand.label, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryRow(stats: DailyStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(title = "今やめた回数", value = stats.stopCount.toString(), modifier = Modifier.weight(1f))
        MetricCard(title = "無視した回数", value = stats.ignoreCount.toString(), modifier = Modifier.weight(1f))
        MetricCard(title = "今日の目標", value = "${stats.goalProgressPercent}%", modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterStateCard(state: AppState) {
    SectionCard(
        title = "Character State",
        subtitle = "介入エージェントとしての状態",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.teto),
                contentDescription = null,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = state.liveMonitor.currentDialogue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip("mood ${state.characterState.mood}")
                    InfoChip("trust ${state.characterState.trustLevel}")
                    InfoChip("dialogue ${state.characterState.lastDialogueType}")
                    InfoChip("foreground ${state.foregroundAppName}")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonitorScreen(
    state: AppState,
    onRefresh: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onDailyGoalChange: (Int) -> Unit,
    onToggleAlerts: () -> Unit,
    onToggleService: (ServiceTarget) -> Unit,
    onResetCooldown: () -> Unit,
) {
    val live = state.liveMonitor
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = "Live Monitor",
                subtitle = "Accessibility Service と Usage Stats の現在値",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard("Foreground", state.foregroundAppName, modifier = Modifier.weight(1f))
                        MetricCard("Current Score", live.currentScore.toString(), modifier = Modifier.weight(1f))
                        MetricCard("Warning Level", live.warningLevel.label, modifier = Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(live.statusLabel, warningColor(live.warningLevel))
                        InfoChip("session ${live.sessionMinutes} min")
                        InfoChip("swipe ${live.swipeBurst}")
                        InfoChip("dwell ${live.dwellSeconds}s")
                        InfoChip("time ${live.timeBand.label}")
                    }
                    Text(
                        text = live.currentDialogue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onRefresh) { Text("状態を更新") }
                        OutlinedButton(onClick = onResetCooldown) { Text("クールダウン解除") }
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Permissions",
                subtitle = "監視に必要な権限導線",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionRow("Accessibility Service", state.permissions.accessibility, onOpenAccessibilitySettings)
                    PermissionRow("Usage Stats", state.permissions.usageStats, onOpenUsageSettings)
                    PermissionRow("Notifications", state.permissions.notifications, onOpenNotificationSettings)
                    OutlinedButton(onClick = onRequestNotificationPermission) {
                        Text("通知権限を再リクエスト")
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Trigger Policy & Settings",
                subtitle = "Prototype.md の閾値・介入頻度・対象サービスを Android 側で調整",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SettingSlider(
                        label = "Warning Threshold",
                        value = state.settings.threshold.toFloat(),
                        valueRange = 45f..85f,
                        steps = 7,
                        display = state.settings.threshold.toString(),
                        onValueChange = { onThresholdChange(it.toInt()) },
                    )
                    SettingSlider(
                        label = "Cooldown Minutes",
                        value = state.settings.cooldownMinutes.toFloat(),
                        valueRange = 1f..10f,
                        steps = 8,
                        display = "${state.settings.cooldownMinutes} min",
                        onValueChange = { onCooldownChange(it.toInt()) },
                    )
                    SettingSlider(
                        label = "Daily Goal",
                        value = state.settings.dailyGoalMinutes.toFloat(),
                        valueRange = 10f..60f,
                        steps = 9,
                        display = "${state.settings.dailyGoalMinutes} min",
                        onValueChange = { onDailyGoalChange(it.toInt()) },
                    )
                    FilterChip(
                        selected = state.settings.alertsEnabled,
                        onClick = onToggleAlerts,
                        label = { Text(if (state.settings.alertsEnabled) "監視オン" else "監視オフ") },
                        leadingIcon = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (service in ServiceTarget.entries) {
                            FilterChip(
                                selected = state.settings.supportedApps.isEnabled(service),
                                onClick = { onToggleService(service) },
                                label = { Text(service.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabScreen(
    state: AppState,
    preset: DemoPreset,
    decision: DetectionDecision,
    onSelectPreset: (DemoPreset) -> Unit,
    onAdvanceSwipe: () -> Unit,
    onTrigger: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = "Detection Lab",
                subtitle = "YouTube Shorts / Instagram Reels / TikTok For You を Kotlin 側で再現",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (item in DemoPreset.entries) {
                            FilterChip(
                                selected = preset == item,
                                onClick = { onSelectPreset(item) },
                                label = { Text(item.title) },
                            )
                        }
                    }
                    Text(preset.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard("Total Score", decision.snapshot.score.toString(), modifier = Modifier.weight(1f))
                        MetricCard("Warning Level", decision.snapshot.warningLevel.label, modifier = Modifier.weight(1f))
                        MetricCard("Time Band", decision.snapshot.timeBand.label, modifier = Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip("session ${decision.snapshot.sessionMinutes} min")
                        InfoChip("relaunch ${decision.snapshot.relaunchCount}")
                        InfoChip("swipe ${decision.snapshot.swipeBurst}")
                        for (keyword in decision.snapshot.keywordHits) {
                            InfoChip(keyword)
                        }
                        for (feature in decision.snapshot.uiFeatures) {
                            InfoChip(feature.label)
                        }
                    }
                    Text(
                        text = decision.snapshot.dialogue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onTrigger) { Text("介入条件を評価") }
                        OutlinedButton(onClick = onAdvanceSwipe) { Text("次の縦スワイプを追加") }
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Score Breakdown",
                subtitle = "w1 * target_app_context + ... の内訳",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (item in decision.snapshot.breakdown) {
                        ElevatedCard {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(item.label, fontWeight = FontWeight.SemiBold)
                                    Text("${item.value}/${item.max}")
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(item.value.toFloat() / item.max.toFloat())
                                            .height(8.dp)
                                            .clip(CircleShape)
                                            .background(warningColor(decision.snapshot.warningLevel)),
                                    )
                                }
                                Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Candidate Detection Signals",
                subtitle = "Prototype.md の検知要素",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (item in listOf(
                        "対象アプリの前景化",
                        "Shorts / Reels / For You 文字列",
                        "全画面縦動画らしいレイアウト",
                        "短い間隔での連続縦スクロール",
                        "警告後すぐ再突入したか",
                    )) {
                        InfoChip(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = "Expected Architecture",
                subtitle = "Context Detection / Sensing / Estimation / Intervention",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((title, items) in architectureLayers) {
                        DetailGroup(title = title, items = items)
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "MVP Scope",
                subtitle = "Must Have / Nice to Have / Out of Scope",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((title, items) in scopeColumns) {
                        DetailGroup(title = title, items = items)
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Roadmap & Evaluation",
                subtitle = "開発優先度と評価計画",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((title, items) in roadmapPhases) {
                        DetailGroup(title = title, items = items)
                    }
                    for ((title, items) in evaluationPlan) {
                        DetailGroup(title = title, items = items)
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Risks & Future Work",
                subtitle = "通知疲れと誤検知を抑えつつ拡張する",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((title, items) in risks) {
                        DetailGroup(title = title, items = items)
                    }
                    DetailGroup(
                        title = "Future Work",
                        items = listOf(
                            "個人ごとの危険時間帯学習",
                            "介入文の最適化",
                            "端末内MLによる高精度判定",
                            "代替行動提案",
                            "キャラクター育成 / 関係性変化",
                            "集中モードやPC拡張との連携",
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (granted) "Granted" else "Needs setup",
                color = if (granted) Color(0xFF58D7C5) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClick) {
            Text("開く")
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(display, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                content()
            },
        )
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoChip(text: String) {
    AssistChip(onClick = {}, label = { Text(text) })
}

@Composable
private fun LogRow(log: SessionLog, zoneId: ZoneId) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(log.appName, fontWeight = FontWeight.SemiBold)
            Text(
                log.formattedRange(zoneId),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("score ${log.uiScore}")
            Text(
                "${log.warningLevel.label} / ${log.userAction.label}",
                color = warningColor(log.warningLevel),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DetailGroup(title: String, items: List<String>) {
        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                for (item in items) {
                    Text("• $item", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

private fun warningColor(level: WarningLevel): Color = when (level) {
    WarningLevel.WATCH -> Color(0xFF90A4AE)
    WarningLevel.LIGHT -> Color(0xFFFFD36A)
    WarningLevel.MEDIUM -> Color(0xFFFFA26D)
    WarningLevel.STRONG -> Color(0xFFFF6B57)
}

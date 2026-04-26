package dev.shortblocker.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shortblocker.app.MainActivity
import dev.shortblocker.app.R
import dev.shortblocker.app.data.AppState
import dev.shortblocker.app.data.DailyStats
import dev.shortblocker.app.data.DemoPreset
import dev.shortblocker.app.data.LauncherLogo
import dev.shortblocker.app.data.ServiceTarget
import dev.shortblocker.app.data.SessionLog
import dev.shortblocker.app.data.UserAction
import dev.shortblocker.app.data.WarningLevel
import dev.shortblocker.app.domain.DetectionDecision
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class AppTab(val route: String, val title: String, val icon: @Composable () -> Unit) {
    DASHBOARD("dashboard", "ホーム", { Icon(Icons.Outlined.Dashboard, contentDescription = null) }),
    MONITOR("monitor", "設定", { Icon(Icons.Outlined.Tune, contentDescription = null) });

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

    LaunchedEffect(requestedTab) {
        currentTab = AppTab.fromRoute(requestedTab)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshRuntimeState(context)
        viewModel.syncLauncherLogo(context)
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Text("ドーパミン警察", fontWeight = FontWeight.ExtraBold)
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
                    onOpenMediaSessionSettings = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onDetectionMinutesChange = viewModel::updateCooldownMinutes,
                    onDailyGoalChange = viewModel::updateDailyGoalMinutes,
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(state: AppState) {
    val todayMinutes = todayUsageMinutes(state.sessionLogs)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeTile(
                        title = "今日の視聴時間",
                        value = formatMinutesAsHourMinute(todayMinutes),
                        colorA = Color.White,
                        colorB = Color(0xFFFFF4E8),
                    )
                    HomeArtCard()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeTile(
                            title = "1日の目標",
                            value = "${state.settings.dailyGoalMinutes}分",
                            modifier = Modifier.weight(1f),
                            colorA = Color.White,
                            colorB = Color(0xFFFFF4E8),
                        )
                        HomeTile(
                            title = "検知タイミング",
                            value = "${state.settings.cooldownMinutes}分",
                            modifier = Modifier.weight(1f),
                            titleFontSize = 15.sp,
                            colorA = Color.White,
                            colorB = Color(0xFFFFF4E8),
                        )
                    }
                    WeeklyUsageCard(sessionLogs = state.sessionLogs)
                }
            }
        }
    }
}

@Composable
private fun HomeArtCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFFD7EA), Color(0xFFD9F7F2), Color(0xFFE5E9FF)),
                ),
            )
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(width = 2.dp, color = Color.White.copy(alpha = 0.75f), shape = RoundedCornerShape(16.dp))
                .background(Color(0x66FFFFFF))
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x66FFFFFF))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(92.dp)) {
                        val stroke = Stroke(width = 16f)
                        drawArc(
                            color = Color(0xFFEF6C9A),
                            startAngle = -90f,
                            sweepAngle = 210f,
                            useCenter = false,
                            style = stroke,
                        )
                        drawArc(
                            color = Color(0xFF31A996),
                            startAngle = 120f,
                            sweepAngle = 95f,
                            useCenter = false,
                            style = stroke,
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = 26f,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x66FFFFFF))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF8FB9)),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF8FD8C9)),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF9CAFFF)),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFC37D)),
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                    ) {
                        val color = Color(0xCCFFFFFF)
                        val points = listOf(
                            Offset(0f, size.height * 0.8f),
                            Offset(size.width * 0.25f, size.height * 0.45f),
                            Offset(size.width * 0.5f, size.height * 0.6f),
                            Offset(size.width * 0.75f, size.height * 0.25f),
                            Offset(size.width, size.height * 0.5f),
                        )
                        for (i in 0 until points.lastIndex) {
                            drawLine(
                                color = color,
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 6f,
                            )
                        }
                    }
                }
            }
            Image(
                painter = painterResource(id = R.drawable.hand),
                contentDescription = "hand",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 26.dp)
                    .size(140.dp),
            )
        }
    }
}

@Composable
private fun WeeklyUsageCard(sessionLogs: List<SessionLog>) {
    val weekly = weeklyUsageMinutes(sessionLogs)
    val maxMinutes = (weekly.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val axisMax = roundUpAxisMinutes(maxMinutes)
    val axisStep = (axisMax / 4).coerceAtLeast(15)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("一週間の使用時間", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 170.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.height(140.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    for (step in 4 downTo 0) {
                        Text(
                            formatMinutesAsHourMinute(axisStep * step),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Black,
                            fontSize = 13.sp,
                        )
                    }
                }
                weekly.forEach { (label, minutes) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((140f * (minutes.toFloat() / axisMax)).dp.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(Color(0xFFFF8C00)),
                        )
                        Text(
                            formatMinutesAsHourMinute(minutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Black,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Black, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun todayUsageMinutes(sessionLogs: List<SessionLog>): Int {
    val today = LocalDate.now()
    return sessionLogs
        .filter { Instant.ofEpochMilli(it.timestampEndEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate() == today }
        .sumOf { ((it.timestampEndEpochMillis - it.timestampStartEpochMillis) / 60_000L).toInt().coerceAtLeast(1) }
}

private fun weeklyUsageMinutes(sessionLogs: List<SessionLog>): List<Pair<String, Int>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = today.minusDays(6L)
    val byDate = sessionLogs.groupBy {
        Instant.ofEpochMilli(it.timestampEndEpochMillis).atZone(zone).toLocalDate()
    }
    return (0..6).map { index ->
        val date = start.plusDays(index.toLong())
        val minutes = byDate[date].orEmpty()
            .sumOf { ((it.timestampEndEpochMillis - it.timestampStartEpochMillis) / 60_000L).toInt().coerceAtLeast(1) }
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPANESE) to minutes
    }
}

private fun formatMinutesAsHourMinute(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}時間${minutes}分"
        hours > 0 -> "${hours}時間"
        else -> "${minutes}分"
    }
}

private fun roundUpAxisMinutes(value: Int): Int {
    val step = when {
        value <= 60 -> 15
        value <= 180 -> 30
        else -> 60
    }
    return ((value + step - 1) / step) * step
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
                        text = "見すぎ防止をサポート",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "今日はゆるく整えていこう",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusBadge(state.liveMonitor.statusLabel, warningColor(state.liveMonitor.warningLevel))
                }
                Spacer(modifier = Modifier.width(12.dp))
                HeroVisual(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(title = "警告回数", value = stats.warningCount.toString(), modifier = Modifier.weight(1f))
                MetricCard(title = "セーブ時間", value = "${stats.estimatedSavedMinutes}分", modifier = Modifier.weight(1f))
                MetricCard(title = "目標達成", value = "${stats.goalProgressPercent}%", modifier = Modifier.weight(1f))
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
            CharacterVisual(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(24.dp)),
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

@Composable
private fun HeroVisual(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFD7EA),
                        Color(0xFFD9F7F2),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .size(width = 44.dp, height = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFFFFF)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(width = 62.dp, height = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFFFFF)),
        )
    }
}

@Composable
private fun CharacterVisual(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFD36A), Color(0xFFFF855D), Color(0xFF5B2A1F)),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFF7F4EF)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .size(width = 66.dp, height = 46.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF142029)),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonitorScreen(
    state: AppState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenMediaSessionSettings: () -> Unit,
    onDetectionMinutesChange: (Int) -> Unit,
    onDailyGoalChange: (Int) -> Unit,
) {
    val goalMinutes = state.settings.dailyGoalMinutes
    val detectionMinutes = state.settings.cooldownMinutes
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "時間設定", subtitle = "") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeSettingTile(
                        label = "1日の目標",
                        minutes = goalMinutes,
                        minMinutes = 10,
                        maxMinutes = 180,
                        onCommitMinutes = onDailyGoalChange,
                        modifier = Modifier.weight(1f),
                    )
                    TimeSettingTile(
                        label = "検知タイミング",
                        minutes = detectionMinutes,
                        minMinutes = 1,
                        maxMinutes = 30,
                        onCommitMinutes = onDetectionMinutesChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            SectionCard(title = "権限", subtitle = "") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PermissionGridTile(
                            title = "操作補助",
                            granted = state.permissions.accessibility,
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.weight(1f),
                        )
                        PermissionGridTile(
                            title = "使用状況",
                            granted = state.permissions.usageStats,
                            onClick = onOpenUsageSettings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PermissionGridTile(
                            title = "メディア情報",
                            granted = state.permissions.mediaSessionListener,
                            onClick = onOpenMediaSessionSettings,
                            modifier = Modifier.weight(1f),
                        )
                        PermissionGridTile(
                            title = "通知",
                            granted = state.permissions.notifications,
                            onClick = onOpenNotificationSettings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    colorA: Color,
    colorB: Color,
    subtitle: String? = null,
    titleFontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colorA, colorB)))
                .border(1.dp, Color(0xFFFF8C00), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFF444444), fontSize = 14.sp, maxLines = 1)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 32.sp)
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (granted) "設定済み" else "未設定",
                color = if (granted) Color(0xFF23A26D) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClick) {
            Text("開く")
        }
    }
}

@Composable
private fun TimeSettingTile(
    label: String,
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    onCommitMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember(minutes) { mutableStateOf(minutes.toString()) }
    fun commitInput() {
        val parsed = input.toIntOrNull() ?: return
        val clamped = parsed.coerceIn(minMinutes, maxMinutes)
        if (clamped != minutes) onCommitMinutes(clamped)
        input = clamped.toString()
    }

    Card(
        modifier = modifier
            .heightIn(min = 150.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF4E8))))
                .border(1.dp, Color(0xFFFF8C00), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, fontWeight = FontWeight.SemiBold, color = Color.Black)
            OutlinedTextField(
                value = input,
                onValueChange = { next ->
                    if (next.all { it.isDigit() }) {
                        input = next
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                label = { Text("設定", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                suffix = { Text("分", fontWeight = FontWeight.ExtraBold) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { commitInput() },
                ),
            )
            TextButton(onClick = { commitInput() }) { Text("設定", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PermissionGridTile(
    title: String,
    granted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF4E8))))
                .border(1.dp, Color(0xFFFF8C00), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.Black, maxLines = 1)
            Text(
                text = if (granted) "設定済み" else "未設定",
                color = if (granted) Color(0xFF23A26D) else Color(0xFF666666),
                maxLines = 1,
            )
            OutlinedButton(onClick = onClick) {
                Text("開く")
            }
        }
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
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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

package dev.shortblocker.app.ui

import android.os.Build
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PsychologyAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.repeatCount
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
import java.time.format.TextStyle
import java.util.Locale

private const val EPOCH_DAY_MILLIS = 24 * 60 * 60 * 1000L

private enum class AppTab(val route: String, val title: String, val icon: @Composable () -> Unit) {
    DASHBOARD("dashboard", "ホーム", { Icon(Icons.Outlined.Home, contentDescription = null) }),
    MONITOR("monitor", "設定", { Icon(Icons.Outlined.Settings, contentDescription = null) });

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
            .background(Color(0xFFFFFCF7))
            .systemBarsPadding(),
        containerColor = Color(0xFFFFFCF7),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFF7EA),
                    titleContentColor = Color(0xFF2D2018),
                ),
                title = {
                    DopaminePoliceTitle()
                },
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = Color(0xFFFFCA7B),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color(0xFFFFF7EA),
                tonalElevation = 0.dp,
            ) {
                for (tab in AppTab.entries) {
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = tab.icon,
                        label = { Text(tab.title, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFF7A00),
                            selectedTextColor = Color(0xFFFF7A00),
                            indicatorColor = Color(0xFFFFE4C8),
                            unselectedIconColor = Color(0xFF6B6660),
                            unselectedTextColor = Color(0xFF6B6660),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color(0xFFFFFCF7),
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
private fun DopaminePoliceTitle() {
    Row(
        modifier = Modifier.offset(x = (-3).dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.gazo),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = buildAnnotatedString {
                append("ドーパミン")
                withStyle(SpanStyle(color = Color(0xFFE60012))) {
                    append("警察")
                }
            },
            color = Color(0xFF2D2018),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
        )
    }
}

@Composable
private fun DashboardScreen(state: AppState) {
    val todayMinutes = detectionSecondsToDisplayMinutes(state.dailyShortsWatchSeconds)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFCF7)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DashboardTodayCard(todayMinutes = todayMinutes) }
        item {
            HomeArtCard(
                todayMinutes = todayMinutes,
                dailyGoalMinutes = state.settings.dailyGoalMinutes,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetricCard(
                    title = "1日の目標",
                    value = "${state.settings.dailyGoalMinutes}分",
                    icon = Icons.Outlined.TrackChanges,
                    accent = Color(0xFFE9547A),
                    soft = Color(0xFFFFF1F5),
                    modifier = Modifier.weight(1f),
                )
                DashboardMetricCard(
                    title = "検知タイミング",
                    value = "${state.settings.cooldownMinutes}分",
                    icon = Icons.Outlined.AccessTime,
                    accent = Color(0xFF7764C9),
                    soft = Color(0xFFF3F0FF),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { WeeklyUsageCard(state = state) }
    }
}

@Composable
private fun DashboardTodayCard(todayMinutes: Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .background(Brush.horizontalGradient(listOf(Color.White, Color(0xFFFFF7EA), Color(0xFFFFFBF4))))
                .border(1.dp, Color(0xFFFFCA7B), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFFF8A00))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text("今日の視聴時間", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                MinutesValueText(
                    value = todayMinutes.toString(),
                    color = Color(0xFFFF8800),
                    numberFontSize = 56.sp,
                    unitFontSize = 42.sp,
                    unitOffsetY = (-6).dp,
                )
            }
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(104.dp),
            ) {
                drawCircle(color = Color(0xFFFFE6B8), radius = size.minDimension / 2f, style = Stroke(width = 10f))
                drawLine(Color(0xFFFFB44E), Offset(size.width / 2f, size.height / 2f), Offset(size.width / 2f, size.height * 0.28f), strokeWidth = 9f)
                drawLine(Color(0xFFFFB44E), Offset(size.width / 2f, size.height / 2f), Offset(size.width * 0.67f, size.height / 2f), strokeWidth = 9f)
                drawCircle(color = Color(0xFFFFB44E), radius = 6f, center = Offset(size.width / 2f, size.height / 2f))
            }
        }
    }
}

private val homeSpeechBubbleMessagesOverGoal = listOf(
    "ちょっと、いつまで見てんのよ。脳が腐るわよ？",
    "君の人生、ショート動画並みに中身スカスカになっちゃうわよ？",
    "そんなに動画が大事なら、一生スマホと仲良くしてれば？",
    "制限時間、とっくに過ぎてるわよ。約束も守れないなんて、子供以下ね。",
)

private val homeSpeechBubbleMessagesWithinGoal = listOf(
    "ショート動画なんかより、私と話してる方が落ち着くでしょ？",
    "動画見て誤魔化さなくても、私がそばにいてあげるから大丈夫よ。",
    "そんなにショート動画が恋しいの？ 今は目の前のことに集中！頑張って！",
    "ショート動画見てる暇があるなら、机に向かって勉強しなさいよ。",
)

@Composable
private fun HomeArtCard(
    todayMinutes: Int,
    dailyGoalMinutes: Int,
) {
    val isOverGoal = todayMinutes > dailyGoalMinutes
    val gifAsset = if (isOverGoal) "home.gif" else "ok.gif"
    val speechText = remember(isOverGoal) {
        if (isOverGoal) {
            homeSpeechBubbleMessagesOverGoal.random()
        } else {
            homeSpeechBubbleMessagesWithinGoal.random()
        }
    }
    val context = LocalContext.current
    val gifImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .background(Brush.linearGradient(listOf(Color(0xFFFFF9FB), Color(0xFFF5FFFC), Color(0xFFF6F7FF))))
                .border(1.dp, Color(0xFFEDE8E0), RoundedCornerShape(24.dp))
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        ) {
            val homeGifWidth = maxWidth.coerceAtMost(340.dp)
            val homeGifHeight = homeGifWidth * (500f / 666f)
            val homeGifVisibleOffset = (-70).dp

            DashboardDonutChart(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 18.dp, start = 2.dp)
                    .size(104.dp),
            )
            DashboardMiniBars(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 12.dp)
                    .size(width = 142.dp, height = 86.dp),
            )
            DashboardLineSparkline(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 8.dp)
                    .size(width = 180.dp, height = 58.dp),
            )
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/$gifAsset")
                    .repeatCount(-1)
                    .crossfade(false)
                    .build(),
                imageLoader = gifImageLoader,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = homeGifVisibleOffset)
                    .size(width = homeGifWidth, height = homeGifHeight),
            )
            HomeSpeechBubble(
                text = speechText,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp, y = (-8).dp),
            )
        }
    }
}

@Composable
private fun HomeSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val bubbleBorder = Color(0xFFC4BFBA)
    val tailWidth = 14.dp
    val bubbleRadius = 16.dp
    Box(
        modifier = modifier
            .widthIn(max = 180.dp)
            .drawBehind {
                val bubblePath = speechBubblePath(
                    size = size,
                    tailWidth = tailWidth.toPx(),
                    tailHeight = 22.dp.toPx(),
                    radius = bubbleRadius.toPx(),
                )
                translate(top = 2.dp.toPx()) {
                    drawPath(bubblePath, color = Color.Black.copy(alpha = 0.10f))
                }
                translate(top = 4.dp.toPx()) {
                    drawPath(bubblePath, color = Color.Black.copy(alpha = 0.04f))
                }
                drawPath(bubblePath, color = Color.White)
                drawPath(bubblePath, color = bubbleBorder, style = Stroke(width = 1.dp.toPx()))
            },
    ) {
        Text(
            text = text,
            color = Color(0xFF3C3027),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = tailWidth + 12.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
        )
    }
}

private fun speechBubblePath(
    size: Size,
    tailWidth: Float,
    tailHeight: Float,
    radius: Float,
): Path {
    val left = tailWidth
    val right = size.width
    val bottom = size.height
    val centerY = size.height * 0.54f
    val halfTail = tailHeight / 2f
    val roundedRadius = radius.coerceAtMost((right - left) / 2f).coerceAtMost(bottom / 2f)

    return Path().apply {
        moveTo(left + roundedRadius, 0f)
        lineTo(right - roundedRadius, 0f)
        quadraticTo(right, 0f, right, roundedRadius)
        lineTo(right, bottom - roundedRadius)
        quadraticTo(right, bottom, right - roundedRadius, bottom)
        lineTo(left + roundedRadius, bottom)
        quadraticTo(left, bottom, left, bottom - roundedRadius)
        lineTo(left, centerY + halfTail)
        lineTo(0f, centerY)
        lineTo(left, centerY - halfTail)
        lineTo(left, roundedRadius)
        quadraticTo(left, 0f, left + roundedRadius, 0f)
        close()
    }
}

@Composable
private fun DashboardDonutChart(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 17f)
        drawArc(Color(0xFFF48AA5), -92f, 145f, false, style = stroke)
        drawArc(Color(0xFF9EA7F4), 62f, 92f, false, style = stroke)
        drawArc(Color(0xFF82D5D0), 165f, 74f, false, style = stroke)
        drawArc(Color(0xFFF8D18C), 250f, 64f, false, style = stroke)
        drawCircle(Color.White.copy(alpha = 0.94f), radius = size.minDimension * 0.28f)
    }
}

@Composable
private fun DashboardMiniBars(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        listOf(44.dp to Color(0xFFA6DFE5), 70.dp to Color(0xFFC6C1FA), 56.dp to Color(0xFFFFCE83)).forEach { (height, color) ->
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(height)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun DashboardLineSparkline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val points = listOf(
            Offset(0f, size.height * 0.82f),
            Offset(size.width * 0.23f, size.height * 0.62f),
            Offset(size.width * 0.43f, size.height * 0.72f),
            Offset(size.width * 0.68f, size.height * 0.37f),
            Offset(size.width, size.height * 0.2f),
        )
        for (i in 0 until points.lastIndex) {
            drawLine(Color(0xFFB5BCF4), points[i], points[i + 1], strokeWidth = 7f)
            drawLine(Color.White.copy(alpha = 0.9f), points[i], points[i + 1], strokeWidth = 3f)
        }
        drawCircle(Color(0xFF8FA0F5), radius = 8f, center = points.last())
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    soft: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color.White, soft)))
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                MinutesValueText(
                    value = value.removeSuffix("分"),
                    color = accent,
                    numberFontSize = 30.sp,
                    unitFontSize = 26.sp,
                    unitOffsetY = (-3).dp,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MinutesValueText(
    value: String,
    color: Color,
    numberFontSize: TextUnit,
    unitFontSize: TextUnit,
    unitOffsetY: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = value,
            color = color,
            maxLines = 1,
            style = minutesTextStyle(numberFontSize),
        )
        Text(
            text = "分",
            modifier = Modifier.offset(y = unitOffsetY),
            color = color,
            maxLines = 1,
            style = minutesTextStyle(unitFontSize),
        )
    }
}

@Suppress("DEPRECATION")
private fun minutesTextStyle(fontSize: TextUnit) = ComposeTextStyle(
    fontWeight = FontWeight.ExtraBold,
    fontSize = fontSize,
    lineHeight = fontSize,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun WeeklyUsageCard(state: AppState) {
    val weekly = weeklyShortsWatchMinutes(state)
    val maxMinutes = (weekly.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val axisMax = roundUpAxisMinutes(maxMinutes)
    val axisStep = (axisMax / 4).coerceAtLeast(15)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFFCF7))))
                .border(1.dp, Color(0xFFFFE1B6), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF0D5)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.BarChart, contentDescription = null, tint = Color(0xFFFF9800))
                }
                Text("一週間の使用時間", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2D2520), fontSize = 20.sp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.height(146.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    for (step in 4 downTo 0) {
                        Text("${axisStep * step}分", color = Color(0xFF55504B), fontSize = 12.sp, maxLines = 1)
                    }
                }
                weekly.forEach { (label, minutes) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text("${minutes}分", color = Color(0xFF3A332D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((128f * (minutes.toFloat() / axisMax)).dp.coerceAtLeast(5.dp))
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFFFF9F1C), Color(0xFFFF7A00)))),
                        )
                        Text(label, color = Color(0xFF3A332D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun detectionSecondsToDisplayMinutes(seconds: Long): Int {
    if (seconds <= 0L) return 0
    return ((seconds + 59L) / 60L).toInt()
}

private fun weeklyShortsWatchMinutes(state: AppState): List<Pair<String, Int>> {
    val zone = ZoneId.systemDefault()
    val todayEpochDays = System.currentTimeMillis() / EPOCH_DAY_MILLIS
    val byEpochDays = state.shortsWatchHistory
        .associate { it.epochDays to it.seconds }
        .toMutableMap()
    if (state.dailyShortsWatchSeconds > 0L) {
        byEpochDays[state.lastResetDateEpochDays] = state.dailyShortsWatchSeconds
    }

    return (0..6).map { index ->
        val epochDays = todayEpochDays - 6L + index
        val date = Instant.ofEpochMilli(epochDays * EPOCH_DAY_MILLIS).atZone(zone).toLocalDate()
        val minutes = detectionSecondsToDisplayMinutes(byEpochDays[epochDays] ?: 0L)
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFCF7)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsSectionCard(
                title = "時間設定",
                icon = Icons.Outlined.AccessTime,
                accent = Color(0xFFFF8A00),
                soft = Color(0xFFFFF7ED),
                border = Color(0xFFFFC37D),
            ) {
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
            SettingsSectionCard(
                title = "権限",
                icon = Icons.Outlined.Shield,
                accent = Color(0xFF8E78D9),
                soft = Color(0xFFFBF8FF),
                border = Color(0xFFD8C9FF),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PermissionGridTile(
                            title = "操作補助",
                            granted = state.permissions.accessibility,
                            icon = Icons.Outlined.PsychologyAlt,
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.weight(1f),
                        )
                        PermissionGridTile(
                            title = "使用状況",
                            granted = state.permissions.usageStats,
                            icon = Icons.Outlined.Analytics,
                            onClick = onOpenUsageSettings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PermissionGridTile(
                            title = "メディア",
                            granted = state.permissions.mediaSessionListener,
                            icon = Icons.Outlined.Dashboard,
                            onClick = onOpenMediaSessionSettings,
                            modifier = Modifier.weight(1f),
                        )
                        PermissionGridTile(
                            title = "通知",
                            granted = state.permissions.notifications,
                            icon = Icons.Outlined.NotificationsActive,
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
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    soft: Color,
    border: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.White, soft)))
                .border(1.dp, border, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon == Icons.Outlined.Shield) {
                    PoliceShieldMark(
                        modifier = Modifier.size(30.dp),
                        accent = accent,
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                }
                Text(title, color = Color(0xFF2D2520), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            content()
        }
    }
}

@Composable
private fun PoliceShieldMark(modifier: Modifier = Modifier, accent: Color) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val shieldPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.5f, height * 0.06f)
            lineTo(width * 0.88f, height * 0.2f)
            lineTo(width * 0.78f, height * 0.67f)
            quadraticTo(width * 0.66f, height * 0.86f, width * 0.5f, height * 0.96f)
            quadraticTo(width * 0.34f, height * 0.86f, width * 0.22f, height * 0.67f)
            lineTo(width * 0.12f, height * 0.2f)
            close()
        }
        drawPath(shieldPath, color = accent.copy(alpha = 0.18f))
        drawPath(shieldPath, color = accent, style = Stroke(width = 3f))
        drawCircle(color = accent.copy(alpha = 0.9f), radius = width * 0.12f, center = Offset(width * 0.5f, height * 0.36f))
        drawLine(accent, Offset(width * 0.28f, height * 0.58f), Offset(width * 0.72f, height * 0.58f), strokeWidth = 3f)
        drawLine(accent, Offset(width * 0.36f, height * 0.72f), Offset(width * 0.64f, height * 0.72f), strokeWidth = 3f)
        drawCircle(color = Color.White.copy(alpha = 0.95f), radius = width * 0.035f, center = Offset(width * 0.34f, height * 0.34f))
        drawCircle(color = Color.White.copy(alpha = 0.95f), radius = width * 0.035f, center = Offset(width * 0.66f, height * 0.34f))
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
            .heightIn(min = 164.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFFCF8))))
                .border(1.dp, Color(0xFFFFB15E), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2D2520), fontSize = 16.sp)
            OutlinedTextField(
                value = input,
                onValueChange = { next ->
                    if (next.all { it.isDigit() }) {
                        input = next
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = Color(0xFF2D2520),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                ),
                suffix = { Text("分", color = Color(0xFF2D2520), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { commitInput() },
                ),
            )
            Button(
                onClick = { commitInput() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00), contentColor = Color.White),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("設定", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun PermissionGridTile(
    title: String,
    granted: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFCFAFF))))
                .border(1.dp, Color(0xFFD8C9FF), RoundedCornerShape(16.dp))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE5FF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF8E78D9), modifier = Modifier.size(21.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2D2520), maxLines = 1, fontSize = 15.sp)
                Text(
                    text = if (granted) "設定済み" else "未設定",
                    color = if (granted) Color(0xFF23A26D) else Color(0xFF776E88),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    fontSize = 13.sp,
                )
                OutlinedButton(
                    onClick = onClick,
                    shape = CircleShape,
                    modifier = Modifier
                        .height(32.dp)
                        .width(76.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("開く", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                }
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF7ED))))
                .border(1.dp, Color(0xFFFFE2B8), RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2B1E17))
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, color = Color(0xFF8D7B69))
                    }
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

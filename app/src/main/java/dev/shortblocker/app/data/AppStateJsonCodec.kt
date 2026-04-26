package dev.shortblocker.app.data

import org.json.JSONArray
import org.json.JSONObject

object AppStateJsonCodec {
    fun encode(state: AppState): String = JSONObject().apply {
        put("settings", encodeSettings(state.settings))
        put("permissions", encodePermissions(state.permissions))
        put("characterState", encodeCharacterState(state.characterState))
        put("liveMonitor", encodeLiveMonitor(state.liveMonitor))
        put("pendingIntervention", state.pendingIntervention?.let(::encodePendingIntervention))
        put("cooldownUntilEpochMillis", state.cooldownUntilEpochMillis)
        put("foregroundAppName", state.foregroundAppName)
        put("foregroundPackageName", state.foregroundPackageName)
        put("dailyShortsWatchSeconds", state.dailyShortsWatchSeconds)
        put("lastResetDateEpochDays", state.lastResetDateEpochDays)
        put("sessionLogs", JSONArray().apply {
            state.sessionLogs.forEach { put(encodeSessionLog(it)) }
        })
    }.toString()

    fun decode(raw: String): AppState {
        if (raw.isBlank()) {
            return AppState()
        }

        val json = JSONObject(raw)
        return AppState(
            settings = decodeSettings(json.optJSONObject("settings")),
            permissions = decodePermissions(json.optJSONObject("permissions")),
            characterState = decodeCharacterState(json.optJSONObject("characterState")),
            liveMonitor = decodeLiveMonitor(json.optJSONObject("liveMonitor")),
            pendingIntervention = json.optJSONObject("pendingIntervention")?.let(::decodePendingIntervention),
            cooldownUntilEpochMillis = json.optLong("cooldownUntilEpochMillis", 0L),
            foregroundAppName = json.optString("foregroundAppName", "未取得"),
            foregroundPackageName = json.optString("foregroundPackageName", ""),
            sessionLogs = decodeSessionLogs(json.optJSONArray("sessionLogs")),
            dailyShortsWatchSeconds = json.optLong("dailyShortsWatchSeconds", 0L),
            lastResetDateEpochDays = json.optLong(
                "lastResetDateEpochDays",
                System.currentTimeMillis() / (1000 * 60 * 60 * 24),
            ),
        )
    }

    private fun encodeSettings(settings: MonitorSettings): JSONObject = JSONObject().apply {
        put("threshold", settings.threshold)
        put("cooldownMinutes", settings.cooldownMinutes)
        put("dailyGoalMinutes", settings.dailyGoalMinutes)
        put("alertsEnabled", settings.alertsEnabled)
        put("launcherLogo", settings.launcherLogo.name)
        put("supportedApps", JSONObject().apply {
            put("youtube", settings.supportedApps.youtube)
            put("instagram", settings.supportedApps.instagram)
            put("tiktok", settings.supportedApps.tiktok)
        })
    }

    private fun decodeSettings(json: JSONObject?): MonitorSettings = MonitorSettings(
        threshold = MonitorSettings().threshold,
        cooldownMinutes = json?.optInt("cooldownMinutes", 4) ?: 4,
        dailyGoalMinutes = json?.optInt("dailyGoalMinutes", 25) ?: 25,
        alertsEnabled = json?.optBoolean("alertsEnabled", true) ?: true,
        launcherLogo = LauncherLogo.fromName(json?.optString("launcherLogo")),
        supportedApps = SupportedApps(
            youtube = json?.optJSONObject("supportedApps")?.optBoolean("youtube", true) ?: true,
            instagram = json?.optJSONObject("supportedApps")?.optBoolean("instagram", true) ?: true,
            tiktok = json?.optJSONObject("supportedApps")?.optBoolean("tiktok", true) ?: true,
        ),
    )

    private fun encodePermissions(snapshot: PermissionSnapshot): JSONObject = JSONObject().apply {
        put("accessibility", snapshot.accessibility)
        put("usageStats", snapshot.usageStats)
        put("notifications", snapshot.notifications)
        put("mediaSessionListener", snapshot.mediaSessionListener)
    }

    private fun decodePermissions(json: JSONObject?): PermissionSnapshot = PermissionSnapshot(
        accessibility = json?.optBoolean("accessibility", false) ?: false,
        usageStats = json?.optBoolean("usageStats", false) ?: false,
        notifications = json?.optBoolean("notifications", false) ?: false,
        mediaSessionListener = json?.optBoolean("mediaSessionListener", false) ?: false,
    )

    private fun encodeCharacterState(state: CharacterState): JSONObject = JSONObject().apply {
        put("characterId", state.characterId)
        put("mood", state.mood)
        put("trustLevel", state.trustLevel)
        put("lastDialogueType", state.lastDialogueType)
    }

    private fun decodeCharacterState(json: JSONObject?): CharacterState = CharacterState(
        characterId = json?.optString("characterId", "guardian_teto") ?: "guardian_teto",
        mood = json?.optString("mood", "watchful") ?: "watchful",
        trustLevel = json?.optInt("trustLevel", 74) ?: 74,
        lastDialogueType = json?.optString("lastDialogueType", "medium") ?: "medium",
    )

    private fun encodeLiveMonitor(state: LiveMonitorState): JSONObject = JSONObject().apply {
        put("currentAppName", state.currentAppName)
        put("currentPackageName", state.currentPackageName)
        put("currentScore", state.currentScore)
        put("warningLevel", state.warningLevel.name)
        put("statusLabel", state.statusLabel)
        put("currentDialogue", state.currentDialogue)
        put("sessionMinutes", state.sessionMinutes)
        put("relaunchCount", state.relaunchCount)
        put("swipeBurst", state.swipeBurst)
        put("dwellSeconds", state.dwellSeconds)
        put("keywordHits", JSONArray(state.keywordHits))
        put("uiFeatures", JSONArray(state.uiFeatures.map { it.name }))
        put("timeBand", state.timeBand.name)
        put("lastUpdatedAtEpochMillis", state.lastUpdatedAtEpochMillis)
    }

    private fun decodeLiveMonitor(json: JSONObject?): LiveMonitorState = LiveMonitorState(
        currentAppName = json?.optString("currentAppName", "待機中") ?: "待機中",
        currentPackageName = json?.optString("currentPackageName", "") ?: "",
        currentScore = json?.optInt("currentScore", 0) ?: 0,
        warningLevel = WarningLevel.fromName(json?.optString("warningLevel")),
        statusLabel = json?.optString("statusLabel", "権限待ち") ?: "権限待ち",
        currentDialogue = json?.optString(
            "currentDialogue",
            "Accessibility Service を有効化すると監視が始まります。MediaSession は精度向上用です。",
        ) ?: "Accessibility Service を有効化すると監視が始まります。MediaSession は精度向上用です。",
        sessionMinutes = json?.optInt("sessionMinutes", 0) ?: 0,
        relaunchCount = json?.optInt("relaunchCount", 0) ?: 0,
        swipeBurst = json?.optInt("swipeBurst", 0) ?: 0,
        dwellSeconds = json?.optInt("dwellSeconds", 0) ?: 0,
        keywordHits = json?.optJSONArray("keywordHits").toStringList(),
        uiFeatures = UiFeature.fromNames(json?.optJSONArray("uiFeatures").toStringList()),
        timeBand = TimeBand.fromName(json?.optString("timeBand")),
        lastUpdatedAtEpochMillis = json?.optLong("lastUpdatedAtEpochMillis", 0L) ?: 0L,
    )

    private fun encodePendingIntervention(pending: PendingIntervention): JSONObject = JSONObject().apply {
        put("id", pending.id)
        put("appName", pending.appName)
        put("packageName", pending.packageName)
        put("score", pending.score)
        put("warningLevel", pending.warningLevel.name)
        put("dialogue", pending.dialogue)
        put("timeBand", pending.timeBand.name)
        put("source", pending.source)
        put("createdAtEpochMillis", pending.createdAtEpochMillis)
    }

    private fun decodePendingIntervention(json: JSONObject): PendingIntervention = PendingIntervention(
        id = json.optString("id"),
        appName = json.optString("appName"),
        packageName = json.optString("packageName"),
        score = json.optInt("score"),
        warningLevel = WarningLevel.fromName(json.optString("warningLevel")),
        dialogue = json.optString("dialogue"),
        timeBand = TimeBand.fromName(json.optString("timeBand")),
        source = json.optString("source"),
        createdAtEpochMillis = json.optLong("createdAtEpochMillis"),
    )

    private fun encodeSessionLog(log: SessionLog): JSONObject = JSONObject().apply {
        put("id", log.id)
        put("timestampStartEpochMillis", log.timestampStartEpochMillis)
        put("timestampEndEpochMillis", log.timestampEndEpochMillis)
        put("appName", log.appName)
        put("uiScore", log.uiScore)
        put("triggeredWarning", log.triggeredWarning)
        put("warningLevel", log.warningLevel.name)
        put("userAction", log.userAction.name)
        put("timeBand", log.timeBand.name)
        put("savedMinutes", log.savedMinutes)
        put("source", log.source)
    }

    private fun decodeSessionLogs(array: JSONArray?): List<SessionLog> {
        if (array == null) {
            return emptyList()
        }

        val logs = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    SessionLog(
                        id = item.optString("id"),
                        timestampStartEpochMillis = item.optLong("timestampStartEpochMillis"),
                        timestampEndEpochMillis = item.optLong("timestampEndEpochMillis"),
                        appName = item.optString("appName"),
                        uiScore = item.optInt("uiScore"),
                        triggeredWarning = item.optBoolean("triggeredWarning", true),
                        warningLevel = WarningLevel.fromName(item.optString("warningLevel")),
                        userAction = UserAction.fromName(item.optString("userAction")),
                        timeBand = TimeBand.fromName(item.optString("timeBand")),
                        savedMinutes = item.optInt("savedMinutes"),
                        source = item.optString("source", "service"),
                    ),
                )
            }
        }

        return logs.filterNot { it.source == "seed" }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                add(optString(index))
            }
        }
    }
}

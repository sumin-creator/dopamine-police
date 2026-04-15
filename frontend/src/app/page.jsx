"use client";

import { useRef, useState } from "react";
import Image from "next/image";
import brainrotImage from "../../images/Brainrot.png";
import tetoImage from "../../images/teto.png";

const serviceKeyByApp = {
  YouTube: "youtube",
  Instagram: "instagram",
  TikTok: "tiktok",
};

const timeBandLabels = {
  focus: "07:00-09:00",
  evening: "19:00-21:00",
  lateNight: "23:00-01:00",
};

const presetDefinitions = {
  youtube: {
    label: "YouTube Shorts",
    note: "通常動画ではなく Shorts 体験を検知するケース",
    state: {
      appName: "YouTube",
      timeBand: "lateNight",
      sessionMinutes: 14,
      relaunchCount: 2,
      swipeBurst: 4,
      dwellSeconds: 26,
      reentryAfterWarning: true,
      keywords: {
        shorts: true,
        reels: false,
        forYou: false,
      },
      uiFlags: {
        fullscreenVertical: true,
        actionRail: true,
        videoStructure: true,
        continuousTransitions: true,
      },
    },
  },
  instagram: {
    label: "Instagram Reels",
    note: "縦動画 UI と右側アクション列が揃ったケース",
    state: {
      appName: "Instagram",
      timeBand: "evening",
      sessionMinutes: 9,
      relaunchCount: 1,
      swipeBurst: 3,
      dwellSeconds: 18,
      reentryAfterWarning: false,
      keywords: {
        shorts: false,
        reels: true,
        forYou: false,
      },
      uiFlags: {
        fullscreenVertical: true,
        actionRail: true,
        videoStructure: true,
        continuousTransitions: true,
      },
    },
  },
  tiktok: {
    label: "TikTok For You",
    note: "深夜帯に再突入を繰り返した強介入ケース",
    state: {
      appName: "TikTok",
      timeBand: "lateNight",
      sessionMinutes: 18,
      relaunchCount: 3,
      swipeBurst: 5,
      dwellSeconds: 34,
      reentryAfterWarning: true,
      keywords: {
        shorts: false,
        reels: false,
        forYou: true,
      },
      uiFlags: {
        fullscreenVertical: true,
        actionRail: true,
        videoStructure: true,
        continuousTransitions: true,
      },
    },
  },
  normal: {
    label: "通常利用",
    note: "ショート動画 UI ではない通常アプリ利用",
    state: {
      appName: "Study Notes",
      timeBand: "focus",
      sessionMinutes: 6,
      relaunchCount: 0,
      swipeBurst: 0,
      dwellSeconds: 6,
      reentryAfterWarning: false,
      keywords: {
        shorts: false,
        reels: false,
        forYou: false,
      },
      uiFlags: {
        fullscreenVertical: false,
        actionRail: false,
        videoStructure: false,
        continuousTransitions: false,
      },
    },
  },
};

const warningDialogues = {
  light: [
    "ねえ、それ今ほんとに開く必要あった？",
    "画面に吸われる前に戻っておいで",
  ],
  medium: [
    "またshort動画の沼に入ろうとしてる",
    "1本だけって、今ので何本目？ ドバガキくんさぁ",
  ],
  strong: [
    "今日はさすがに見すぎ",
    "未来の自分に怒られるやつだよ",
  ],
  lateNight: [
    "この時間のShorts、ほぼ事故だよ",
    "寝る前の1本が一番危ないよ",
  ],
};

const userFlowSteps = [
  "1. ユーザーが YouTube / Instagram / TikTok を開く",
  "2. UI特徴と操作イベントから short-video score を計算する",
  "3. 閾値を超えるとキャラクター通知が出る",
  "4. ユーザーが通知をタップする",
  "5. やめる / あと1分 / 無視する / 今日の目標を見る を提示する",
  "6. 選択内容がログに保存される",
  "7. ダッシュボードに回避回数と危険時間帯が反映される",
];

const architectureLayers = [
  {
    title: "Context Detection Layer",
    items: ["前景アプリ", "使用時間", "時刻", "セッション継続時間"],
  },
  {
    title: "UI / Interaction Sensing Layer",
    items: [
      "Accessibility Service",
      "画面テキスト",
      "View階層",
      "スクロール / スワイプイベント",
    ],
  },
  {
    title: "Short-Video State Estimation Layer",
    items: ["ルールベース判定", "特徴量スコアリング", "将来は軽量ML分類器"],
  },
  {
    title: "Intervention Layer",
    items: ["キャラクター通知", "ポップアップ介入", "行動選択", "利用ログ保存"],
  },
];

const dataModels = [
  {
    title: "SessionLog",
    fields: [
      "id",
      "timestamp_start",
      "timestamp_end",
      "app_name",
      "ui_score",
      "triggered_warning",
      "warning_level",
      "user_action",
    ],
  },
  {
    title: "DailyStats",
    fields: [
      "date",
      "warning_count",
      "stop_count",
      "ignore_count",
      "estimated_saved_minutes",
      "most_risky_time_band",
    ],
  },
  {
    title: "CharacterState",
    fields: ["character_id", "mood", "trust_level", "last_dialogue_type"],
  },
];

const techStack = [
  {
    title: "Mobile",
    items: ["Android", "Kotlin", "Android native bridge"],
  },
  {
    title: "Core Android APIs",
    items: [
      "Accessibility Service",
      "Usage Stats Manager",
      "Notifications",
      "Room / SQLite / DataStore",
    ],
  },
  {
    title: "Optional ML",
    items: [
      "ロジスティック回帰",
      "LightGBM / XGBoost",
      "端末内 lightweight classifier",
    ],
  },
  {
    title: "Design / Assets",
    items: ["2D キャラ立ち絵", "表情差分", "通知用イラスト"],
  },
];

const scopeColumns = [
  {
    title: "Must Have",
    items: [
      "Androidアプリの基本構造",
      "Accessibility Service ベースの UI 検知",
      "1〜3種類の短尺動画UIへの対応",
      "キャラクター通知",
      "行動選択肢",
      "ログ保存",
      "ダッシュボード表示",
    ],
  },
  {
    title: "Nice to Have",
    items: [
      "キャラクターの表情差分",
      "状況依存セリフ",
      "軽量ML分類器",
      "無視が続いた時の反応変化",
      "守れた時間の推定",
    ],
  },
  {
    title: "Out of Scope",
    items: [
      "高精度CVモデルの常時解析",
      "サーバー前提の個人最適化",
      "Live2Dや高度なアニメーション",
      "iOS完全対応",
      "複雑なクラウド同期",
    ],
  },
];

const roadmapPhases = [
  {
    title: "Phase 1",
    items: [
      "Androidプロジェクト初期化",
      "Accessibility Service セットアップ",
      "対象アプリの前景検知",
      "基本通知",
    ],
  },
  {
    title: "Phase 2",
    items: [
      "UI特徴抽出",
      "short-video score 実装",
      "キャラ通知文の出し分け",
      "ログ保存",
    ],
  },
  {
    title: "Phase 3",
    items: [
      "ダッシュボードUI",
      "デモ調整",
      "キャラ表情差分",
      "発表資料作成",
    ],
  },
];

const evaluationCards = [
  {
    title: "Functional Evaluation",
    items: [
      "対象UIを正しく検知できるか",
      "通知が所定条件で発火するか",
      "ログが正しく保存されるか",
    ],
  },
  {
    title: "UX Evaluation",
    items: [
      "キャラクター通知が見たくなるか",
      "無機質な警告より止まりやすいか",
      "うるさすぎないか",
    ],
  },
  {
    title: "Demo Success Criteria",
    items: [
      "短尺動画視聴状態の検知が見せられる",
      "キャラ通知の面白さが伝わる",
      "ダッシュボードで価値が可視化される",
    ],
  },
];

const risksAndMitigations = [
  {
    title: "Technical Risks",
    items: [
      "アプリごとに UI 構造が違い汎化が難しい",
      "Accessibility 実装は端末差と癖がある",
      "バックグラウンド権限説明が必要",
    ],
  },
  {
    title: "UX Risks",
    items: [
      "通知が多すぎると鬱陶しい",
      "キャラクターが飽きられる",
      "誤検知で通常利用まで邪魔する",
    ],
  },
  {
    title: "Mitigation",
    items: [
      "閾値を高めに設定する",
      "介入頻度を制限する",
      "通知強度を出し分ける",
      "MVPでは対象サービスを限定する",
    ],
  },
];

const futureWork = [
  "個人ごとの危険時間帯学習",
  "介入文の最適化",
  "端末内MLによる高精度判定",
  "代替行動提案",
  "キャラクター育成 / 関係性変化",
  "集中モードやPC拡張との連携",
];

const initialLogs = [
  {
    id: "session-1",
    timestampStart: "23:10",
    timestampEnd: "23:23",
    appName: "TikTok",
    uiScore: 92,
    triggeredWarning: true,
    warningLevel: "strong",
    userAction: "stop",
    timeBand: "lateNight",
    savedMinutes: 14,
  },
  {
    id: "session-2",
    timestampStart: "19:42",
    timestampEnd: "19:50",
    appName: "Instagram",
    uiScore: 67,
    triggeredWarning: true,
    warningLevel: "medium",
    userAction: "extend",
    timeBand: "evening",
    savedMinutes: 2,
  },
  {
    id: "session-3",
    timestampStart: "07:26",
    timestampEnd: "07:31",
    appName: "YouTube",
    uiScore: 58,
    triggeredWarning: true,
    warningLevel: "light",
    userAction: "ignore",
    timeBand: "focus",
    savedMinutes: 0,
  },
];

function cloneState(state) {
  return {
    ...state,
    keywords: { ...state.keywords },
    uiFlags: { ...state.uiFlags },
  };
}

function getWarningLevel(score) {
  if (score >= 85) {
    return "strong";
  }

  if (score >= 65) {
    return "medium";
  }

  if (score >= 45) {
    return "light";
  }

  return "watch";
}

function formatLevel(level) {
  return {
    watch: "監視",
    light: "Light",
    medium: "Medium",
    strong: "Strong",
  }[level];
}

function formatAction(action) {
  return {
    stop: "今やめる",
    extend: "あと1分だけ",
    ignore: "無視する",
  }[action];
}

function calculateDetection(simulation, settings) {
  const serviceKey = serviceKeyByApp[simulation.appName];
  const appSupported = serviceKey ? settings.supportedApps[serviceKey] : false;
  const activeKeywords = Object.values(simulation.keywords).filter(Boolean).length;

  const targetAppContext = appSupported
    ? Math.min(30, 18 + Math.min(simulation.relaunchCount, 3) * 4)
    : 0;

  const shortsLikeUi = Math.min(
    35,
    activeKeywords * 6 +
      (simulation.uiFlags.fullscreenVertical ? 8 : 0) +
      (simulation.uiFlags.actionRail ? 7 : 0) +
      (simulation.uiFlags.videoStructure ? 8 : 0) +
      (simulation.uiFlags.continuousTransitions ? 6 : 0)
  );

  const repeatedVerticalNavigation = Math.min(
    20,
    simulation.swipeBurst * 4 + (simulation.reentryAfterWarning ? 4 : 0)
  );

  const sessionDuration = Math.min(
    15,
    Math.round(
      simulation.sessionMinutes * 0.65 + Math.min(simulation.dwellSeconds, 30) * 0.1
    )
  );

  const riskyTimeOfDay =
    simulation.timeBand === "lateNight" ? 10 : simulation.timeBand === "evening" ? 4 : 0;

  const total = Math.min(
    100,
    targetAppContext +
      shortsLikeUi +
      repeatedVerticalNavigation +
      sessionDuration +
      riskyTimeOfDay
  );

  const level = getWarningLevel(total);

  return {
    total,
    level,
    breakdown: [
      {
        label: "Target App Context",
        value: targetAppContext,
        max: 30,
        detail: `${simulation.appName} / 再突入 ${simulation.relaunchCount}回`,
      },
      {
        label: "Shorts-like UI",
        value: shortsLikeUi,
        max: 35,
        detail: `キーワード ${activeKeywords}件 / UI特徴 ${Object.values(
          simulation.uiFlags
        ).filter(Boolean).length}件`,
      },
      {
        label: "Repeated Vertical Navigation",
        value: repeatedVerticalNavigation,
        max: 20,
        detail: `縦スワイプ ${simulation.swipeBurst}回 / 警告後再突入 ${
          simulation.reentryAfterWarning ? "あり" : "なし"
        }`,
      },
      {
        label: "Session Duration",
        value: sessionDuration,
        max: 15,
        detail: `${simulation.sessionMinutes}分継続 / 滞在 ${simulation.dwellSeconds}秒`,
      },
      {
        label: "Risky Time Of Day",
        value: riskyTimeOfDay,
        max: 10,
        detail: timeBandLabels[simulation.timeBand],
      },
    ],
  };
}

function getDialogue(level, simulation) {
  if (level === "watch") {
    return "まだ通常利用の範囲。必要以上に邪魔せず、見守りモードで待機する。";
  }

  const pool =
    simulation.timeBand === "lateNight"
      ? warningDialogues.lateNight
      : warningDialogues[level] || warningDialogues.light;

  const index =
    (simulation.sessionMinutes + simulation.swipeBurst + simulation.relaunchCount) % pool.length;

  return pool[index];
}

function getCharacterMood(level, action) {
  if (action === "stop") {
    return "relieved";
  }

  if (action === "ignore") {
    return "annoyed";
  }

  if (action === "extend") {
    return "watchful";
  }

  if (level === "strong") {
    return "serious";
  }

  if (level === "medium") {
    return "stern";
  }

  return "curious";
}

function formatClock(totalMinutes) {
  const normalized = ((totalMinutes % 1440) + 1440) % 1440;
  const hours = String(Math.floor(normalized / 60)).padStart(2, "0");
  const minutes = String(normalized % 60).padStart(2, "0");
  return `${hours}:${minutes}`;
}

function getSessionWindow(simulation) {
  const baseByBand = {
    focus: 7 * 60,
    evening: 19 * 60 + 30,
    lateNight: 23 * 60 + 5,
  };
  const end = baseByBand[simulation.timeBand] + simulation.sessionMinutes;
  const start = end - Math.max(2, simulation.sessionMinutes);

  return {
    start: formatClock(start),
    end: formatClock(end),
  };
}

function buildLogEntry(simulation, detection, action) {
  const sessionWindow = getSessionWindow(simulation);

  return {
    id: `session-${Date.now()}`,
    timestampStart: sessionWindow.start,
    timestampEnd: sessionWindow.end,
    appName: simulation.appName,
    uiScore: detection.total,
    triggeredWarning: true,
    warningLevel: detection.level,
    userAction: action,
    timeBand: simulation.timeBand,
    savedMinutes:
      action === "stop" ? Math.max(8, Math.round(simulation.sessionMinutes * 0.8)) : action === "extend" ? 2 : 0,
  };
}

function deriveStats(logs, goalMinutes) {
  const warningCount = logs.filter((log) => log.triggeredWarning).length;
  const stopCount = logs.filter((log) => log.userAction === "stop").length;
  const ignoreCount = logs.filter((log) => log.userAction === "ignore").length;
  const estimatedSavedMinutes = logs.reduce((sum, log) => sum + (log.savedMinutes || 0), 0);

  const riskyBandCounts = logs.reduce((accumulator, log) => {
    if (!log.triggeredWarning) {
      return accumulator;
    }

    accumulator[log.timeBand] = (accumulator[log.timeBand] || 0) + 1;
    return accumulator;
  }, {});

  const mostRiskyTimeBand =
    Object.entries(riskyBandCounts).sort((left, right) => right[1] - left[1])[0]?.[0] || "focus";

  return {
    date: "Today",
    warningCount,
    stopCount,
    ignoreCount,
    estimatedSavedMinutes,
    mostRiskyTimeBand,
    goalProgress: Math.min(100, Math.round((estimatedSavedMinutes / goalMinutes) * 100)),
  };
}

function SectionIntro({ eyebrow, title, description }) {
  return (
    <div className="section-intro">
      <span className="eyebrow">{eyebrow}</span>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

function MetricCard({ label, value, detail, tone = "default" }) {
  return (
    <article className={`metric-card metric-card-${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <p>{detail}</p>
    </article>
  );
}

function Meter({ label, value, max, detail }) {
  const width = `${Math.max(6, Math.round((value / max) * 100))}%`;

  return (
    <article className="meter-card">
      <div className="meter-head">
        <span>{label}</span>
        <strong>
          {value}/{max}
        </strong>
      </div>
      <div className="meter-track">
        <div className="meter-fill" style={{ width }} />
      </div>
      <p>{detail}</p>
    </article>
  );
}

export default function Home() {
  const dashboardRef = useRef(null);
  const [activePreset, setActivePreset] = useState("youtube");
  const [simulation, setSimulation] = useState(cloneState(presetDefinitions.youtube.state));
  const [settings, setSettings] = useState({
    threshold: 62,
    cooldownMinutes: 4,
    dailyGoalMinutes: 25,
    alertsEnabled: true,
    supportedApps: {
      youtube: true,
      instagram: true,
      tiktok: true,
    },
    permissions: {
      accessibility: true,
      usageStats: true,
      notifications: true,
      storage: true,
    },
  });
  const [characterState, setCharacterState] = useState({
    characterId: "guardian-teto",
    mood: "watchful",
    trustLevel: 74,
    lastDialogueType: "medium",
  });
  const [sessionLogs, setSessionLogs] = useState(initialLogs);
  const [intervention, setIntervention] = useState(null);
  const [snoozeUntilMinute, setSnoozeUntilMinute] = useState(0);
  const [goalPulse, setGoalPulse] = useState(false);

  const detection = calculateDetection(simulation, settings);
  const stats = deriveStats(sessionLogs, settings.dailyGoalMinutes);
  const currentServiceKey = serviceKeyByApp[simulation.appName];
  const currentServiceSupported = currentServiceKey
    ? settings.supportedApps[currentServiceKey]
    : false;
  const permissionsReady =
    settings.permissions.accessibility &&
    settings.permissions.usageStats &&
    settings.permissions.notifications &&
    settings.permissions.storage;
  const cooldownActive = simulation.sessionMinutes < snoozeUntilMinute;
  const previewDialogue = intervention?.dialogue || getDialogue(detection.level, simulation);
  const actionReady = Boolean(intervention);

  const monitoringState = !settings.alertsEnabled
    ? "監視停止"
    : !permissionsReady
      ? "権限待ち"
      : currentServiceKey && !currentServiceSupported
        ? "対象外アプリ"
        : cooldownActive
          ? `クールダウン中 (${snoozeUntilMinute - simulation.sessionMinutes}分)`
          : detection.total >= settings.threshold
            ? "介入候補"
            : "監視中";

  function scrollToDashboard() {
    dashboardRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    setGoalPulse(true);
    window.setTimeout(() => setGoalPulse(false), 1400);
  }

  function updateSimulation(updater) {
    setActivePreset("custom");
    setSimulation((previous) => updater(previous));
  }

  function evaluateIntervention(nextSimulation = simulation, source = "manual") {
    const nextDetection = calculateDetection(nextSimulation, settings);
    const nextServiceKey = serviceKeyByApp[nextSimulation.appName];
    const nextServiceSupported = nextServiceKey ? settings.supportedApps[nextServiceKey] : false;
    const nextCooldownActive = nextSimulation.sessionMinutes < snoozeUntilMinute;

    if (
      !settings.alertsEnabled ||
      !permissionsReady ||
      (nextServiceKey && !nextServiceSupported) ||
      nextCooldownActive ||
      nextDetection.total < settings.threshold
    ) {
      return;
    }

    setIntervention({
      source,
      score: nextDetection.total,
      level: nextDetection.level,
      dialogue: getDialogue(nextDetection.level, nextSimulation),
      appName: nextSimulation.appName,
    });
    setCharacterState((previous) => ({
      ...previous,
      mood: getCharacterMood(nextDetection.level),
      lastDialogueType: nextDetection.level,
    }));
  }

  function handlePresetChange(presetKey) {
    const preset = presetDefinitions[presetKey];
    setActivePreset(presetKey);
    setIntervention(null);
    setSnoozeUntilMinute(0);
    setSimulation(cloneState(preset.state));
  }

  function handleSwipeProgress() {
    const nextSimulation = {
      ...simulation,
      sessionMinutes: Math.min(30, simulation.sessionMinutes + 1),
      swipeBurst: Math.min(6, simulation.swipeBurst + 1),
      dwellSeconds: Math.min(45, simulation.dwellSeconds + 6),
      reentryAfterWarning: simulation.reentryAfterWarning || snoozeUntilMinute > 0,
      uiFlags: {
        ...simulation.uiFlags,
        continuousTransitions: true,
      },
    };

    setActivePreset("custom");
    setSimulation(nextSimulation);
    evaluateIntervention(nextSimulation, "swipe");
  }

  function handleAction(action) {
    if (!intervention) {
      return;
    }

    const entry = buildLogEntry(simulation, detection, action);
    setSessionLogs((previous) => [entry, ...previous].slice(0, 8));
    setIntervention(null);

    if (action === "stop") {
      setCharacterState((previous) => ({
        ...previous,
        mood: getCharacterMood(detection.level, action),
        trustLevel: Math.min(100, previous.trustLevel + 8),
        lastDialogueType: detection.level,
      }));
      setActivePreset("normal");
      setSimulation(cloneState(presetDefinitions.normal.state));
      setSnoozeUntilMinute(0);
      return;
    }

    if (action === "extend") {
      const nextSimulation = {
        ...simulation,
        sessionMinutes: Math.min(30, simulation.sessionMinutes + 1),
        dwellSeconds: Math.min(45, simulation.dwellSeconds + 4),
        reentryAfterWarning: true,
      };
      setActivePreset("custom");
      setSimulation(nextSimulation);
      setSnoozeUntilMinute(nextSimulation.sessionMinutes + 1);
      setCharacterState((previous) => ({
        ...previous,
        mood: getCharacterMood(detection.level, action),
        trustLevel: Math.max(0, previous.trustLevel - 1),
        lastDialogueType: "extend",
      }));
      return;
    }

    const nextSimulation = {
      ...simulation,
      sessionMinutes: Math.min(30, simulation.sessionMinutes + 2),
      swipeBurst: Math.min(6, simulation.swipeBurst + 1),
      reentryAfterWarning: true,
    };
    setActivePreset("custom");
    setSimulation(nextSimulation);
    setSnoozeUntilMinute(nextSimulation.sessionMinutes + settings.cooldownMinutes);
    setCharacterState((previous) => ({
      ...previous,
      mood: getCharacterMood(detection.level, action),
      trustLevel: Math.max(0, previous.trustLevel - 6),
      lastDialogueType: "ignore",
    }));
  }

  return (
    <main className="prototype-shell">
      <div className="background-grid" />

      <header className="hero-panel" id="top">
        <nav className="top-nav">
          <div className="brand-lockup">
            <span className="brand-mark">Shortblocker</span>
            <span className="brand-subtitle">UI-based short-video intervention prototype</span>
          </div>
          <div className="nav-links">
            <a href="#dashboard">Dashboard</a>
            <a href="#detector">Detector</a>
            <a href="#architecture">Architecture</a>
            <a href="#roadmap">Roadmap</a>
          </div>
        </nav>

        <div className="hero-grid">
          <section className="hero-copy">
            <span className="eyebrow">Short-Video Intervention App</span>
            <h1>アプリ名ではなく、UI体験そのものを検知して止めに入る。</h1>
            <p>
              TikTok / YouTube Shorts / Instagram Reels のような短尺動画 UI を
              Accessibility と操作パターンから推定し、キャラクターが「やめる / あと1分 / 無視する /
              今日の目標を見る」を提示する Web プロトタイプです。
            </p>

            <div className="hero-tags">
              <span>UIベース検知</span>
              <span>キャラクター介入</span>
              <span>行動選択</span>
              <span>ログ可視化</span>
            </div>

            <div className="hero-actions">
              <a href="#detector" className="primary-link">
                検知シミュレーションへ
              </a>
              <a href="#dashboard" className="secondary-link">
                ダッシュボードを見る
              </a>
            </div>

            <div className="problem-grid">
              <article className="info-card">
                <h3>Problem Statement</h3>
                <ul>
                  <li>1本が短く、離脱判断を先送りしやすい</li>
                  <li>縦スワイプで刺激が連続供給される</li>
                  <li>「少しだけ」が崩れやすい</li>
                  <li>既存のアプリ単位ブロックは粒度が粗い</li>
                </ul>
              </article>
              <article className="info-card">
                <h3>Primary Goal</h3>
                <ul>
                  <li>ショート動画視聴状態を UI ベースで検知</li>
                  <li>一定条件でキャラクター通知を出す</li>
                  <li>ログを可視化して見すぎを自覚しやすくする</li>
                  <li>禁止ではなく選択肢を提示する</li>
                </ul>
              </article>
            </div>
          </section>

          <aside className="hero-device">
            <div className="device-frame">
              <div className="device-screen">
                <div className="device-header">
                  <span>{simulation.appName}</span>
                  <span>{monitoringState}</span>
                </div>
                <div className="device-visual">
                  <div className="video-ribbon">SHORT LOOP</div>
                  <Image src={brainrotImage} alt="Short-video spiral illustration" priority />
                </div>
                <div className="speech-card">
                  <div className="speech-portrait">
                    <Image src={tetoImage} alt="Character illustration" priority />
                  </div>
                  <div>
                    <span className="speech-label">Character Intervention</span>
                    <p>{previewDialogue}</p>
                    <div className="speech-meta">
                      <span>{formatLevel(detection.level)}</span>
                      <span>score {detection.total}</span>
                      <span>mood {characterState.mood}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="persona-card">
              <h3>Character Direction</h3>
              <div className="persona-tags">
                <span>可愛い</span>
                <span>少し茶化す</span>
                <span>本気で止める時は止める</span>
              </div>
              <p>
                単なる装飾ではなく、見すぎへの気づき、離脱のきっかけ、選択の促進、継続利用の体験価値を担当します。
              </p>
            </div>
          </aside>
        </div>
      </header>

      <section className="content-section" id="dashboard" ref={dashboardRef}>
        <SectionIntro
          eyebrow="Dashboard"
          title="回避回数・危険時間帯・信頼度を1画面で確認"
          description="DailyStats と CharacterState を中心に、今日どれだけ沼に吸われたか、どれだけ止まれたかを可視化します。"
        />

        <div className={`dashboard-grid ${goalPulse ? "dashboard-pulse" : ""}`}>
          <MetricCard
            label="警告回数"
            value={stats.warningCount}
            detail="triggered_warning が true の回数"
            tone="warm"
          />
          <MetricCard
            label="今やめた回数"
            value={stats.stopCount}
            detail="キャラクター介入から止まれた回数"
            tone="cool"
          />
          <MetricCard
            label="無視した回数"
            value={stats.ignoreCount}
            detail="通知疲れの兆候として監視"
            tone="default"
          />
          <MetricCard
            label="推定セーブ時間"
            value={`${stats.estimatedSavedMinutes} min`}
            detail="stop / extend のログから推定"
            tone="cool"
          />
          <MetricCard
            label="最も危険な時間帯"
            value={timeBandLabels[stats.mostRiskyTimeBand]}
            detail="most_risky_time_band"
            tone="warm"
          />
          <MetricCard
            label="今日の目標"
            value={`${stats.goalProgress}%`}
            detail={`${settings.dailyGoalMinutes}分の目標に対する進捗`}
            tone="default"
          />
        </div>

        <div className="status-grid">
          <article className="status-card">
            <h3>Character State</h3>
            <div className="status-values">
              <span>character_id: {characterState.characterId}</span>
              <span>mood: {characterState.mood}</span>
              <span>trust_level: {characterState.trustLevel}</span>
              <span>last_dialogue_type: {characterState.lastDialogueType}</span>
            </div>
            <div className="trust-bar">
              <div style={{ width: `${characterState.trustLevel}%` }} />
            </div>
          </article>

          <article className="status-card">
            <h3>Target Users</h3>
            <ul>
              <li>少しだけのつもりで長時間 Shorts / Reels を見てしまう人</li>
              <li>勉強や作業中にショート動画へ吸われやすい人</li>
              <li>スクリーンタイム機能では止めきれない人</li>
              <li>制限より軽い介入や見守りの方が続きやすい人</li>
            </ul>
          </article>

          <article className="status-card">
            <h3>Non-Goals</h3>
            <ul>
              <li>iOS 完全対応</li>
              <li>全SNSサービスへの完全対応</li>
              <li>高頻度スクリーンキャプチャ解析</li>
              <li>複雑なクラウド同期や大規模プロファイリング</li>
            </ul>
          </article>
        </div>
      </section>

      <section className="content-section" id="detector">
        <SectionIntro
          eyebrow="Detection Lab"
          title="Context / UI / Interaction から short-video score を算出"
          description="MVP では前景アプリ変化や UI イベント発生時だけ軽量判定し、バッテリー消費を抑えながら短尺動画らしさをスコアリングします。"
        />

        <div className="lab-grid">
          <article className="lab-card">
            <div className="lab-head">
              <h3>Scenario Presets</h3>
              <p>少なくとも 1〜3 種類の短尺動画 UI に対応する前提で、主要ケースを切り替えます。</p>
            </div>
            <div className="preset-grid">
              {Object.entries(presetDefinitions).map(([key, preset]) => (
                <button
                  key={key}
                  type="button"
                  className={`preset-button ${activePreset === key ? "preset-active" : ""}`}
                  onClick={() => handlePresetChange(key)}
                >
                  <strong>{preset.label}</strong>
                  <span>{preset.note}</span>
                </button>
              ))}
            </div>

            <div className="controls-grid">
              <label className="field">
                <span>Foreground App</span>
                <select
                  value={simulation.appName}
                  onChange={(event) =>
                    updateSimulation((previous) => ({
                      ...previous,
                      appName: event.target.value,
                    }))
                  }
                >
                  <option value="YouTube">YouTube</option>
                  <option value="Instagram">Instagram</option>
                  <option value="TikTok">TikTok</option>
                  <option value="Study Notes">Study Notes</option>
                </select>
              </label>

              <div className="field">
                <span>Time Band</span>
                <div className="chip-row">
                  {Object.entries(timeBandLabels).map(([key, label]) => (
                    <button
                      key={key}
                      type="button"
                      className={`chip ${simulation.timeBand === key ? "chip-active" : ""}`}
                      onClick={() =>
                        updateSimulation((previous) => ({
                          ...previous,
                          timeBand: key,
                        }))
                      }
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              <label className="field">
                <span>Session Duration: {simulation.sessionMinutes} min</span>
                <input
                  type="range"
                  min="0"
                  max="30"
                  value={simulation.sessionMinutes}
                  onChange={(event) =>
                    updateSimulation((previous) => ({
                      ...previous,
                      sessionMinutes: Number(event.target.value),
                    }))
                  }
                />
              </label>

              <label className="field">
                <span>Relaunch Count: {simulation.relaunchCount}</span>
                <input
                  type="range"
                  min="0"
                  max="3"
                  value={simulation.relaunchCount}
                  onChange={(event) =>
                    updateSimulation((previous) => ({
                      ...previous,
                      relaunchCount: Number(event.target.value),
                    }))
                  }
                />
              </label>

              <label className="field">
                <span>Vertical Swipe Burst: {simulation.swipeBurst}</span>
                <input
                  type="range"
                  min="0"
                  max="6"
                  value={simulation.swipeBurst}
                  onChange={(event) =>
                    updateSimulation((previous) => ({
                      ...previous,
                      swipeBurst: Number(event.target.value),
                    }))
                  }
                />
              </label>

              <label className="field">
                <span>Dwell Seconds: {simulation.dwellSeconds}</span>
                <input
                  type="range"
                  min="0"
                  max="45"
                  value={simulation.dwellSeconds}
                  onChange={(event) =>
                    updateSimulation((previous) => ({
                      ...previous,
                      dwellSeconds: Number(event.target.value),
                    }))
                  }
                />
              </label>
            </div>

            <div className="detector-flags">
              <div className="flag-group">
                <span>Keyword Signals</span>
                <div className="chip-row">
                  {[
                    ["shorts", "Shorts"],
                    ["reels", "Reels"],
                    ["forYou", "For You"],
                  ].map(([key, label]) => (
                    <button
                      key={key}
                      type="button"
                      className={`chip ${
                        simulation.keywords[key] ? "chip-active" : ""
                      }`}
                      onClick={() =>
                        updateSimulation((previous) => ({
                          ...previous,
                          keywords: {
                            ...previous.keywords,
                            [key]: !previous.keywords[key],
                          },
                        }))
                      }
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="flag-group">
                <span>UI Features</span>
                <div className="chip-row">
                  {[
                    ["fullscreenVertical", "全画面縦動画"],
                    ["actionRail", "右側アクション列"],
                    ["videoStructure", "動画視聴画面構造"],
                    ["continuousTransitions", "連続遷移"],
                  ].map(([key, label]) => (
                    <button
                      key={key}
                      type="button"
                      className={`chip ${
                        simulation.uiFlags[key] ? "chip-active" : ""
                      }`}
                      onClick={() =>
                        updateSimulation((previous) => ({
                          ...previous,
                          uiFlags: {
                            ...previous.uiFlags,
                            [key]: !previous.uiFlags[key],
                          },
                        }))
                      }
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="flag-group">
                <span>Interaction Features</span>
                <button
                  type="button"
                  className={`chip ${
                    simulation.reentryAfterWarning ? "chip-active" : ""
                  }`}
                  onClick={() =>
                    updateSimulation((previous) => ({
                      ...previous,
                      reentryAfterWarning: !previous.reentryAfterWarning,
                    }))
                  }
                >
                  警告後すぐ再突入
                </button>
              </div>
            </div>

            <div className="lab-actions">
              <button type="button" className="primary-button" onClick={() => evaluateIntervention()}>
                介入条件を評価
              </button>
              <button type="button" className="secondary-button" onClick={handleSwipeProgress}>
                次の縦スワイプを追加
              </button>
            </div>
          </article>

          <article className="lab-card">
            <div className="lab-head">
              <h3>Score Estimator</h3>
              <p>
                score = w1 * target_app_context + w2 * shorts_like_ui + w3 *
                repeated_vertical_navigation + w4 * session_duration + w5 * risky_time_of_day
              </p>
            </div>

            <div className="score-hero">
              <div>
                <span>Total Score</span>
                <strong>{detection.total}</strong>
                <p>
                  閾値 {settings.threshold} / warning level {formatLevel(detection.level)}
                </p>
              </div>
              <div className={`score-badge score-${detection.level}`}>{monitoringState}</div>
            </div>

            <div className="meter-stack">
              {detection.breakdown.map((item) => (
                <Meter
                  key={item.label}
                  label={item.label}
                  value={item.value}
                  max={item.max}
                  detail={item.detail}
                />
              ))}
            </div>

            <div className="detection-notes">
              <article>
                <h4>Candidate Detection Signals</h4>
                <ul>
                  <li>対象アプリの前景化</li>
                  <li>Shorts / Reels / For You 文字列</li>
                  <li>全画面縦動画らしいレイアウト</li>
                  <li>短い間隔での連続縦スクロール</li>
                  <li>警告後すぐ再突入したか</li>
                </ul>
              </article>
              <article>
                <h4>ML Extension</h4>
                <ul>
                  <li>ロジスティック回帰</li>
                  <li>LightGBM / XGBoost</li>
                  <li>端末内の小規模分類器</li>
                </ul>
              </article>
            </div>
          </article>
        </div>
      </section>

      <section className="content-section">
        <SectionIntro
          eyebrow="Intervention"
          title="通知で止めるのではなく、相棒として割り込む"
          description="ユーザーに選ばせることを重視し、通知疲れを避けるためにクールダウンと対応アプリ設定を設けています。"
        />

        <div className="intervention-grid">
          <article className="character-panel">
            <div className="character-visual">
              <Image src={tetoImage} alt="Character standing illustration" priority />
            </div>
            <div className="character-copy">
              <span className="eyebrow">Live Dialogue</span>
              <h3>{previewDialogue}</h3>
              <p>
                警告レベル {formatLevel(intervention?.level || detection.level)} / 検知元{" "}
                {intervention?.source || "preview"} / 現在の mood {characterState.mood}
              </p>
              <div className="choice-row">
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => handleAction("stop")}
                  disabled={!actionReady}
                >
                  今やめる
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => handleAction("extend")}
                  disabled={!actionReady}
                >
                  あと1分だけ
                </button>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => handleAction("ignore")}
                  disabled={!actionReady}
                >
                  無視する
                </button>
                <button type="button" className="ghost-button" onClick={scrollToDashboard}>
                  今日の目標を見る
                </button>
              </div>
            </div>
          </article>

          <article className="settings-panel">
            <h3>Trigger Policy & Settings</h3>
            <div className="settings-grid">
              <label className="field">
                <span>Warning Threshold: {settings.threshold}</span>
                <input
                  type="range"
                  min="45"
                  max="85"
                  value={settings.threshold}
                  onChange={(event) =>
                    setSettings((previous) => ({
                      ...previous,
                      threshold: Number(event.target.value),
                    }))
                  }
                />
              </label>

              <label className="field">
                <span>Cooldown: {settings.cooldownMinutes} min</span>
                <input
                  type="range"
                  min="1"
                  max="10"
                  value={settings.cooldownMinutes}
                  onChange={(event) =>
                    setSettings((previous) => ({
                      ...previous,
                      cooldownMinutes: Number(event.target.value),
                    }))
                  }
                />
              </label>

              <label className="field">
                <span>Daily Goal: {settings.dailyGoalMinutes} min</span>
                <input
                  type="range"
                  min="10"
                  max="60"
                  step="5"
                  value={settings.dailyGoalMinutes}
                  onChange={(event) =>
                    setSettings((previous) => ({
                      ...previous,
                      dailyGoalMinutes: Number(event.target.value),
                    }))
                  }
                />
              </label>
            </div>

            <div className="setting-block">
              <span>対象サービス</span>
              <div className="chip-row">
                {[
                  ["youtube", "YouTube Shorts"],
                  ["instagram", "Instagram Reels"],
                  ["tiktok", "TikTok / For You"],
                ].map(([key, label]) => (
                  <button
                    key={key}
                    type="button"
                    className={`chip ${settings.supportedApps[key] ? "chip-active" : ""}`}
                    onClick={() =>
                      setSettings((previous) => ({
                        ...previous,
                        supportedApps: {
                          ...previous.supportedApps,
                          [key]: !previous.supportedApps[key],
                        },
                      }))
                    }
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            <div className="setting-block">
              <span>Permissions</span>
              <div className="chip-row">
                {[
                  ["accessibility", "Accessibility Service"],
                  ["usageStats", "Usage Stats"],
                  ["notifications", "Notifications"],
                  ["storage", "Local persistence"],
                ].map(([key, label]) => (
                  <button
                    key={key}
                    type="button"
                    className={`chip ${settings.permissions[key] ? "chip-active" : ""}`}
                    onClick={() =>
                      setSettings((previous) => ({
                        ...previous,
                        permissions: {
                          ...previous.permissions,
                          [key]: !previous.permissions[key],
                        },
                      }))
                    }
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            <div className="setting-block">
              <span>Monitoring Switch</span>
              <button
                type="button"
                className={`chip ${settings.alertsEnabled ? "chip-active" : ""}`}
                onClick={() =>
                  setSettings((previous) => ({
                    ...previous,
                    alertsEnabled: !previous.alertsEnabled,
                  }))
                }
              >
                {settings.alertsEnabled ? "監視オン" : "監視オフ"}
              </button>
            </div>
          </article>
        </div>
      </section>

      <section className="content-section">
        <SectionIntro
          eyebrow="History"
          title="SessionLog と DailyStats を時系列で保持"
          description="選択内容は stop / extend / ignore として保存され、危険時間帯と推定セーブ時間に反映されます。"
        />

        <div className="history-grid">
          <article className="history-card">
            <h3>Recent SessionLog</h3>
            <div className="log-table">
              <div className="log-row log-head">
                <span>time</span>
                <span>app</span>
                <span>score</span>
                <span>level</span>
                <span>action</span>
              </div>
              {sessionLogs.map((log) => (
                <div className="log-row" key={log.id}>
                  <span>
                    {log.timestampStart} - {log.timestampEnd}
                  </span>
                  <span>{log.appName}</span>
                  <span>{log.uiScore}</span>
                  <span>{formatLevel(log.warningLevel)}</span>
                  <span>{formatAction(log.userAction)}</span>
                </div>
              ))}
            </div>
          </article>

          <article className="history-card">
            <h3>Example User Flow</h3>
            <ol className="flow-list">
              {userFlowSteps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          </article>
        </div>
      </section>

      <section className="content-section" id="architecture">
        <SectionIntro
          eyebrow="Architecture"
          title="検知エンジン、介入エンジン、アプリUIを分離"
          description="Expected Architecture と Data Model を UI 上で確認できるようにし、ハッカソン MVP の説明責任もこの画面で完結させます。"
        />

        <div className="architecture-grid">
          {architectureLayers.map((layer) => (
            <article className="architecture-card" key={layer.title}>
              <h3>{layer.title}</h3>
              <ul>
                {layer.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>
          ))}
        </div>

        <div className="bottom-grid">
          <article className="detail-card">
            <h3>Data Model</h3>
            <div className="field-clusters">
              {dataModels.map((model) => (
                <div key={model.title} className="field-cluster">
                  <strong>{model.title}</strong>
                  <div className="field-tags">
                    {model.fields.map((field) => (
                      <span key={field}>{field}</span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </article>

          <article className="detail-card">
            <h3>Tech Stack</h3>
            <div className="stack-columns">
              {techStack.map((group) => (
                <div key={group.title}>
                  <strong>{group.title}</strong>
                  <ul>
                    {group.items.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </article>
        </div>
      </section>

      <section className="content-section" id="roadmap">
        <SectionIntro
          eyebrow="Scope & Roadmap"
          title="MVP の境界を保ちながらデモ映えを作る"
          description="Must Have / Nice to Have / Out of Scope を切り分け、Phase ごとの優先度、リスク、評価計画、将来拡張まで一覧化しています。"
        />

        <div className="scope-grid">
          {scopeColumns.map((column) => (
            <article className="detail-card" key={column.title}>
              <h3>{column.title}</h3>
              <ul>
                {column.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>
          ))}
        </div>

        <div className="bottom-grid">
          <article className="detail-card">
            <h3>Development Priorities</h3>
            <div className="stack-columns">
              {roadmapPhases.map((phase) => (
                <div key={phase.title}>
                  <strong>{phase.title}</strong>
                  <ul>
                    {phase.items.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </article>

          <article className="detail-card">
            <h3>Evaluation Plan</h3>
            <div className="stack-columns">
              {evaluationCards.map((group) => (
                <div key={group.title}>
                  <strong>{group.title}</strong>
                  <ul>
                    {group.items.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </article>
        </div>

        <div className="bottom-grid">
          <article className="detail-card">
            <h3>Risks & Mitigation</h3>
            <div className="stack-columns">
              {risksAndMitigations.map((group) => (
                <div key={group.title}>
                  <strong>{group.title}</strong>
                  <ul>
                    {group.items.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </article>

          <article className="detail-card">
            <h3>Future Work</h3>
            <ul>
              {futureWork.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
            <div className="pitch-note">
              <strong>Pitch Summary</strong>
              <p>
                高刺激な UI に飲まれる瞬間を検知し、人が自分の選択を取り戻すきっかけを作る。その核を
                UI 理解とキャラクター介入で成立させるのがこのプロトタイプです。
              </p>
            </div>
          </article>
        </div>
      </section>

      {intervention ? (
        <div className="intervention-modal">
          <div className="modal-card">
            <span className="eyebrow">Trigger Fired</span>
            <h3>
              {intervention.appName} / score {intervention.score} / {formatLevel(intervention.level)}
            </h3>
            <p>{intervention.dialogue}</p>
            <div className="choice-row">
              <button type="button" className="primary-button" onClick={() => handleAction("stop")}>
                今やめる
              </button>
              <button
                type="button"
                className="secondary-button"
                onClick={() => handleAction("extend")}
              >
                あと1分だけ
              </button>
              <button
                type="button"
                className="ghost-button"
                onClick={() => handleAction("ignore")}
              >
                無視する
              </button>
              <button type="button" className="ghost-button" onClick={scrollToDashboard}>
                今日の目標を見る
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}

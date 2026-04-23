# Shortblocker Detection Logic

`ShortVideoAccessibilityService` と `ShortVideoDetector` の実ランタイム検知フローを図にしたメモです。

## Flow

```mermaid
flowchart TD
    A[Accessibility event<br/>window state / content changed / view scrolled] --> B{event.packageName == self app?}
    B -- yes --> Z1[ignore]
    B -- no --> C[ShortVideoDetector.processEvent]

    C --> D{ServiceTarget == YouTube?}
    D -- no --> E[clear activeSession<br/>score=0 / shouldTrigger=false]
    D -- yes --> F[collectSignals<br/>event text + contentDescription + className<br/>+ node tree text/viewId]

    F --> G[detectKeywords<br/>shorts / ショート / youtube shorts<br/>viewId: short / shorts / reel]
    F --> H[detectActionHints<br/>like / comment / share / save / follow ...]

    G --> I[retain keyword hints<br/>TTL 20s]
    H --> J[retain action hints<br/>TTL 12s]

    I --> K{retained keywordHits >= 1<br/>and retained actionHints >= 2 ?}
    J --> K
    K -- yes --> L[viewer evidence]
    K -- no --> M[not enough viewer evidence]

    L --> N{new Shorts surface hint now?}
    M --> N
    N -- yes --> O[refresh reliable evidence<br/>TTL 12s]
    N -- no --> P[keep previous reliable evidence only if still fresh]

    O --> Q{TYPE_VIEW_SCROLLED and<br/>vertical scroll?}
    P --> Q
    Q -- no --> R[keep swipeBurst]
    Q -- yes --> S{fresh reliable evidence?}
    S -- no --> T[reset swipeBurst=0]
    S -- yes --> U[count swipe with debounce 900ms<br/>burst window 20s<br/>telemetry only]

    R --> V[resolve scoreable evidence]
    T --> V
    U --> V

    V --> W{current viewer evidence<br/>or continuing shorts scroll?}
    W -- no --> X[scoreable evidence cleared<br/>keywords/actionHints/swipeBurst = 0]
    W -- yes --> Y[keep scoreable evidence]

    X --> Z[detectUiFeatures]
    Y --> Z[detectUiFeatures<br/>VIDEO_STRUCTURE / FULLSCREEN_VERTICAL<br/>ACTION_RAIL / CONTINUOUS_TRANSITIONS]

    Z --> AA[evaluateScenario<br/>score = app context + UI + duration]
    AA --> AB{alertsEnabled<br/>YouTube enabled<br/>canIntervene<br/>cooldown finished<br/>reliable short-video evidence<br/>score >= threshold}

    AB -- no --> Z2[shouldTrigger=false]
    AB -- yes --> AC{last warning > 30s ago?}
    AC -- no --> Z2
    AC -- yes --> AD[shouldTrigger=true<br/>show intervention notification or overlay fallback]
```

## Key Conditions

| Item | Current value | Meaning |
| --- | --- | --- |
| `threshold` | `62` | 通知候補になるスコア閾値 |
| `REQUIRED_SHORTS_SWIPES` | `2` | `CONTINUOUS_TRANSITIONS` 表示用の目安 |
| `MIN_ACTION_RAIL_HINTS` | `2` | viewer evidence に必要な action hint 数 |
| `SHORTS_KEYWORD_TTL_MS` | `20_000` | Shorts surface hint の保持時間 |
| `SHORTS_ACTION_HINT_TTL_MS` | `12_000` | action hint の保持時間 |
| `SHORTS_EVIDENCE_TTL_MS` | `12_000` | 信頼証拠の保持時間 |
| `SHORTS_SWIPE_DEBOUNCE_MS` | `900` | 重複 scroll event の除外時間 |
| `SCROLL_BURST_WINDOW_MS` | `20_000` | swipe burst を継続加算する時間窓 |
| `WARNING_RATE_LIMIT_MS` | `30_000` | 通知の rate limit |

## Notes

- 実ランタイムの `processEvent()` は現状 `YouTube` 以外を即除外します。
- `README.md` には Instagram / TikTok も書かれていますが、通知発火ロジックはまだ YouTube Shorts 寄りです。
- 時間帯は `timeBand` として保持されますが、スコア加点には使っていません。
- `keyword` と `action hint` は別 TTL で保持し、信頼証拠の更新は新しい Shorts surface hint を見た時だけ行います。
- `Usage Stats` と通知権限は監視表示には残りますが、発火可否の必須条件からは外しています。
- `swipeBurst` はログと `CONTINUOUS_TRANSITIONS` の補助指標として残していますが、通知発火条件とスコア計算には使っていません。
- UI 上の「介入候補」は `score >= threshold` で出ますが、通知発火はそれより厳しい条件です。

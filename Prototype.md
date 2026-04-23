# Short-Video Intervention App

> UIベースでショート動画視聴状態を検知し、キャラクターが介入してユーザーを“沼”から引き戻す Android アプリ

---

## 1. Overview

本プロジェクトは、TikTok / YouTube Shorts / Instagram Reels などの**短尺動画UI**を、アプリ単位ではなく**画面・UIベース**で検知し、ユーザーが没入しすぎる前にキャラクターが通知や介入を行うモバイルアプリである。

従来のスクリーンタイム系アプリは、アプリ単位で利用時間を制限するものが多い。しかし、現実には YouTube のような汎用アプリ内でも Shorts のような高刺激な体験だけを問題視したいケースがある。本プロジェクトでは、その点に着目し、**「どのアプリを開いたか」ではなく「今どのようなUI体験をしているか」** を推定対象とする。

さらに、単なる無機質な警告ではなく、**可愛いアニメ調キャラクターが状況に応じて止めに入る**ことで、冷たい制限アプリではなく、行動介入型の相棒として体験を設計する。

---

## 2. Problem Statement

短尺動画は以下の特徴により、ユーザーが意図以上に時間を消費しやすい。

* 1本あたりの消費時間が短く、離脱判断を先送りしやすい
* 縦スワイプで次々に新しい刺激が供給される
* 報酬予測誤差の大きいコンテンツ消費体験になりやすい
* 「少しだけ見る」の自己制御が崩れやすい

この問題に対して、既存の対策は次のような限界を持つ。

* アプリ単位ブロックでは雑すぎる
* 通知が無機質で無視されやすい
* ユーザーの文脈や視聴状態を見ていない

そこで本プロジェクトでは、

1. **UI構造と操作パターンから短尺動画視聴状態を推定する**
2. **完全没入前のタイミングでキャラクターが介入する**
3. **禁止ではなく選択肢を提示し、行動を取り戻させる**

ことを目的とする。

---

## 3. Goals

### Primary Goal

* ショート動画視聴状態をUIベースで検知する
* 一定条件でキャラクター通知を出す
* 「やめる」「あと1分」「無視する」などの行動選択を提供する
* ログを可視化し、見すぎを自覚しやすくする

### Secondary Goal

* キャラクターによる介入体験を差別化要素にする
* 将来的にMLベースの賢い判定や個人最適化につなげる

### Non-Goals (Hackathon MVP)

* iOS完全対応
* すべてのSNS/動画サービスへの完全対応
* 高頻度スクリーンキャプチャによる重い画像認識
* 複雑なクラウド同期機能
* 大規模なユーザープロファイリング

---

## 4. Target Users

* ショート動画を“少しだけ”のつもりで長時間見てしまう人
* 勉強や作業中に Shorts / Reels / TikTok に吸われやすい人
* スクリーンタイム機能では止めきれない人
* 制限よりも、軽い介入や見守りの方が続きやすい人

---

## 5. Core Concept

### Key Idea

**短尺動画をアプリではなく「UI体験」として捉える。**

たとえば YouTube という1つのアプリの中にも、通常動画視聴・検索・コメント閲覧・Shorts視聴など異なる体験がある。本プロジェクトは、対象アプリを一律にブロックするのではなく、**短尺動画特有のUIや操作パターン**を利用して、問題のある体験だけに介入する。

### Intervention Style

介入は制裁ではなく、**キャラクターによる割り込み**として行う。

例:

* 「ねえ、また吸われてるよ」
* 「1本だけのつもり、もう何本目？」
* 「今止まるなら、まだ助かる」
* 「この時間のShorts、かなり危ないよ」

---

## 6. System Overview

システムは大きく以下の4層からなる。

1. **Context Detection Layer**

   * 前景アプリ
   * 使用時間
   * セッション継続時間

2. **UI / Interaction Sensing Layer**

   * Accessibility Service によるUIイベント取得
   * 画面テキスト
   * View階層
   * スクロール / スワイプ様の遷移イベント

3. **Short-Video State Estimation Layer**

   * ルールベース判定
   * 特徴量スコアリング
   * 将来的には軽量ML分類器

4. **Intervention Layer**

   * キャラクター通知
   * ポップアップ or アプリ内介入
   * 行動選択肢
   * 利用ログ保存

---

## 7. Expected Architecture

```text
[Android OS]
   ├─ Usage Stats / Foreground App Detection
   ├─ Accessibility Service
   │    ├─ window content changes
   │    ├─ view tree / text hints
   │    └─ scroll / interaction events
   └─ Notification / Overlay APIs

[Detection Engine]
   ├─ app context analyzer
   ├─ UI feature extractor
   ├─ interaction pattern tracker
   └─ short-video score estimator

[Intervention Engine]
   ├─ trigger policy
   ├─ character dialogue selector
   ├─ notification sender
   └─ action logger

[App UI]
   ├─ dashboard
   ├─ settings
   ├─ character screen
   └─ statistics / history
```

---

## 8. Detection Design

### 8.1 Detection Policy

MVPでは、**常時重い解析を行うのではなく、前景アプリ変化やUIイベント発生時に軽量判定を行う**。

これにより、

* バッテリー消費を抑える
* ハッカソン実装の安定性を高める
* バックグラウンド動作の説明を簡潔にする

### 8.2 Candidate Detection Signals

#### Context Features

* 対象アプリの前景化
* 起動からの経過時間
* 深夜帯かどうか
* 短時間での再起動回数

#### UI Features

* `Shorts`, `Reels`, `For You` などの文字列
* 全画面縦動画らしいレイアウト
* 右側の縦並びアクション列
* 動画視聴画面に特徴的な構造
* ビュー遷移の連続性

#### Interaction Features

* 短い間隔での連続縦スクロール
* 同種画面間の高速遷移
* 一定時間以上の滞在
* 一度警告後すぐに再突入したか

### 8.3 Short-Video Score

MVPでは以下のような簡易スコアリングを想定する。

```text
score =
  w1 * target_app_context
+ w2 * shorts_like_ui
+ w3 * repeated_vertical_navigation
+ w4 * session_duration
+ w5 * risky_time_of_day
```

スコアが閾値を超えた場合、通知候補となる。

### 8.4 ML Extension

将来的には、ルールベースで集めた特徴量をもとに、

* ロジスティック回帰
* LightGBM / XGBoost
* 小規模な端末内分類器

などで short-video screen probability を推定することを検討する。

---

## 9. Character Intervention Design

### 9.1 Character Role

キャラクターは単なる装飾ではなく、**介入エージェント**として設計する。

役割:

* 見すぎに気づかせる
* 離脱のきっかけを与える
* 選択を促す
* 継続利用の体験価値を作る

### 9.2 Personality Direction

想定する方向性:

* 可愛い
* 少し茶化す
* でも本気で止める時は止める

### 9.3 Dialogue Examples

#### Light Warning

* 「ねえ、それ今ほんとに開く必要あった？」
* 「画面に吸われる前に戻っておいで」

#### Medium Warning

* 「またshort動画の沼に入ろうとしてる」
* 「1本だけって、今ので何本目？ドバガキくんさぁ」

#### Strong Warning

* 「今日はさすがに見すぎ」
* 「未来の自分に怒られるやつだよ」

#### Late Night Warning

* 「この時間のShorts、ほぼ事故だよ」
* 「寝る前の1本が一番危ないよ」

### 9.4 User Choices

通知や介入画面で、以下のような選択肢を用意する。

* 今やめる
* あと1分だけ
* 無視する
* 今日の目標を見る

禁止一辺倒ではなく、**ユーザーに自分で選ばせる**ことを重視する。

---

## 10. MVP Scope

### Must Have

* Androidアプリ
* バックグラウンド監視の基本構造
* Accessibility Service ベースのUI検知
* 少なくとも1〜3種類の短尺動画UIに対応
* キャラクター通知
* 基本的な行動選択肢
* ログ保存
* ダッシュボード表示

### Nice to Have

* キャラクターの表情差分
* 状況依存セリフ
* 軽量ML分類器
* 無視し続けた時の反応変化
* 今日守れた時間の推定

### Out of Scope for Hackathon

* 高精度CVモデルによる常時スクリーン解析
* サーバーサイド前提の個人最適化
* Live2Dや高度なアニメーション

---

## 11. Example User Flow

1. ユーザーが YouTube / Instagram / TikTok を開く
2. UI特徴と操作イベントから short-video score を計算する
3. 閾値を超えるとキャラクター通知が出る
4. ユーザーが通知をタップする
5. キャラクターが「やめる / あと1分 / 無視する」を提示する
6. 選択内容がログに保存される
7. ダッシュボードにその日の回避回数や危険時間帯が表示される

---

## 12. Data Model (Draft)

### SessionLog

* `id`
* `timestamp_start`
* `timestamp_end`
* `app_name`
* `ui_score`
* `triggered_warning` (bool)
* `warning_level`
* `user_action` (`stop` / `extend` / `ignore`)

### DailyStats

* `date`
* `warning_count`
* `stop_count`
* `ignore_count`
* `estimated_saved_minutes`
* `most_risky_time_band`

### CharacterState

* `character_id`
* `mood`
* `trust_level`
* `last_dialogue_type`

---

## 13. Tech Stack (Draft)

### Mobile

* Android (Kotlin + Android native bridge)

### Core Android APIs

* Accessibility Service
* Usage Stats Manager
* Notifications
* Local persistence (Room / SQLite / DataStore)

### Optional ML

* On-device lightweight classifier
* TensorFlow Lite or simple custom scoring logic

### Design / Assets

* 2D character illustrations
* expression variants (normal / warning / angry / sad / tired)

---

## 14. Risks and Challenges

### Technical Risks

* UI情報がアプリごとに異なり、汎化が難しい
* Accessibilityベース検知は実装に癖がある
* バックグラウンド制約や権限取得の導線が必要
* 常時監視っぽく見えるため、説明責任が重要

### UX Risks

* 通知が多すぎると鬱陶しくなる
* キャラクターが飽きられる可能性
* 誤検知で通常利用まで邪魔するリスク

### Mitigation

* 閾値を高めに設定する
* 介入頻度を制限する
* 状況に応じて通知強度を変える
* MVPでは対象サービスを限定する

---

## 15. Evaluation Plan

Hackathon段階では、以下の観点で評価する。

### Functional Evaluation

* 対象UIを正しく検知できるか
* 通知が所定条件で発火するか
* ログが正しく保存されるか

### UX Evaluation

* キャラクター通知が見たくなるか
* 無機質な警告より止まりやすいと感じるか
* うるさすぎないか

### Demo Success Criteria

* デモ中に短尺動画視聴状態の検知が見せられる
* キャラ通知の面白さが伝わる
* ダッシュボードで価値が可視化される

---

## 16. Future Work

* 個人ごとの危険時間帯学習
* 介入文の最適化
* 端末内MLによる高精度判定
* 代替行動提案（散歩、ポモドーロ、メモ）
* キャラクターの育成 / 関係性変化
* 集中モードとの連携
* PC拡張やブラウザ版補助

---

## 17. Pitch Summary

本プロジェクトは、短尺動画の見すぎを単なるアプリ制限の問題としてではなく、**UI体験への没入**として捉える。

Android上で画面構造と操作パターンをもとに短尺動画視聴状態を推定し、可愛いキャラクターが適切なタイミングで介入することで、ユーザーが行動選択を取り戻せるよう支援する。

技術面では UIベース検知、体験面ではキャラクター介入を核とし、ハッカソンでは実装可能性とデモ映えの両立を狙う。

---

## 18. Temporary Project Name Ideas

---

## 19. Development Priorities

### Phase 1

* Androidプロジェクト初期化
* Accessibility Service のセットアップ
* 対象アプリの前景検知
* 基本通知

### Phase 2

* UI特徴抽出
* short-video score の実装
* キャラ通知文の出し分け
* ログ保存

### Phase 3

* ダッシュボードUI
* デモ調整
* キャラ表情差分
* 発表資料作成

---

## 20. Final Note

このプロジェクトの価値は、単に「ショート動画を止める」ことではない。

**高刺激なUIに飲まれる瞬間を検知し、人が自分の選択を取り戻すためのきっかけを作ること**が本質である。

そのために、技術としては UI理解、体験としてはキャラクター介入を組み合わせる。

ハッカソンでは、まずこの核をぶらさずに MVP を成立させることを最優先とする。

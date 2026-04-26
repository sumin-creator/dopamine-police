# ドーパミン警察
`Prototype.md` をもとに、Web プロトタイプから `Kotlin + Android` アプリへ移行したプロジェクトです。

ランタイムの主役は `app/` 配下の Android アプリです。Jetpack Compose で UI を構成し、`Accessibility Service` と `Usage Stats` と `Notification` アクションで短尺動画の見すぎ介入を行います。

## 実装済み
- Kotlin + Android + Jetpack Compose のアプリ本体
- `Accessibility Service` ベースの短尺動画 UI 検知
- YouTube Shorts / Instagram Reels / TikTok For You の 3 系統を対象にしたルールベース判定
- キャラクター介入通知と `今やめる / あと1分だけ / 無視する / 今日の目標を見る`
- `DataStore` による `SessionLog` / `CharacterState` / 設定 / 監視状態の保存
- ダッシュボード、監視設定、検知ラボ、仕様一覧の Android UI
- ランチャーアイコンを `ドーパミン警察` テーマの 6 種から切替可能（警察/サイレン + ショート動画検知）

## ディレクトリ
- `app/`: Android アプリ本体
- `Prototype.md`: 元仕様

## ビルド
Android Studio でこのリポジトリを開くか、ルートで以下を実行してください。

```bash
./gradlew assembleDebug
```

生成される APK:

```bash
app/build/outputs/apk/debug/app-debug.apk
```

## 初回セットアップ
アプリ起動後、以下を有効化してください。

1. `Accessibility Service`
2. `Usage Stats`
3. 通知権限

監視状態は `Monitor` タブで確認できます。

## ロゴ切替
`Monitor` タブの `Trigger Policy & Settings` 内にある `App Icon` からロゴを切り替えできます。  
インストール直後は `警察バッジ + 動画スキャン` がデフォルトで表示されます。

## 開発メモ
- `MainActivity` が Compose UI の入口です
- `ShortVideoAccessibilityService` が UI イベントを監視します
- `ShortVideoDetector` が short-video score を計算します
- `NotificationActionReceiver` が通知アクションを処理します
- `AppStateStore` が永続状態を保存します

## 検証
2026-04-15 時点でローカルで以下を実行し、`assembleDebug` の成功を確認しています。

```bash
./gradlew --no-daemon assembleDebug
```

gif更新

debugをmainにあげるようのコメント
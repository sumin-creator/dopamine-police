# ドーパミン警察

ショート動画を見続けてしまう状態を検知し、キャラクター通知と警告オーバーレイで止めに入る Android アプリです。

## 概要

- YouTube Shorts の画面構造を Accessibility Service で取得
- 画面テキスト / View ID / ノード位置から「Shorts らしさ」をスコア化
- 一定時間以上「見続けている」と判断したら介入（通知 / オーバーレイ）
- 視聴時間を日次・週次で記録

## 開発の目的

ショート動画は、1本だけのつもりでも次々と見てしまいやすい UI です。  
このアプリは視聴を強制ブロックせず、見続けているタイミングを検知して「今やめるきっかけ」を作ることを目的にしています。

## 主な機能

- YouTube Shorts のルールベース検知
- Shorts らしさのスコアリングと閾値超え時間の計測
- 目標時間 / 検知タイミング設定
- キャラクター通知、GIF + サイレン警告オーバーレイ
- DataStore への設定 / 視聴時間保存

## 画面

- ホーム: 今日の視聴時間、週間履歴、目標時間
- 設定: 権限状態、1日の目標、検知タイミング

## セットアップ

初回起動後、次の権限を有効化してください。

1. 操作補助
2. 使用状況
3. メディア
4. 通知


## ビルド

```bash
./gradlew assembleDebug
```

生成 APK:

```bash
app/build/outputs/apk/debug/app-debug.apk
```

## UI

<p align="center">
  <img src="./slide/ui_yukari_good.gif" alt="UI good" width="49%" />
  <img src="./slide/ui_yukari_bad.gif" alt="UI bad" width="49%" />
</p>

## 発表スライド

![ドーパミン警察 スライド 01](./slide/dopamine-police-01.png)
![ドーパミン警察 スライド 02](./slide/dopamine-police-02.png)
![ドーパミン警察 スライド 03](./slide/dopamine-police-03.png)
![ドーパミン警察 スライド 04](./slide/dopamine-police-04.png)
![ドーパミン警察 スライド 05](./slide/dopamine-police-05.png)
![ドーパミン警察 スライド 06](./slide/dopamine-police-06.png)
![ドーパミン警察 スライド 07](./slide/dopamine-police-07.png)
![ドーパミン警察 スライド 08](./slide/dopamine-police-08.png)
![ドーパミン警察 スライド 09](./slide/dopamine-police-09.png)
![ドーパミン警察 スライド 10](./slide/dopamine-police-10.png)
![ドーパミン警察 スライド 11](./slide/dopamine-police-11.png)
![ドーパミン警察 スライド 12](./slide/dopamine-police-12.png)
![ドーパミン警察 スライド 13](./slide/dopamine-police-13.png)
![ドーパミン警察 スライド 14](./slide/dopamine-police-14.png)
![ドーパミン警察 スライド 15](./slide/dopamine-police-15.png)
![ドーパミン警察 スライド 16](./slide/dopamine-police-16.png)

## 動作例

<p align="center">
  <video src="https://github.com/user-attachments/assets/3afbaab4-5b8c-433a-95d1-64c97ef092ee" controls width="70%"></video>
</p>

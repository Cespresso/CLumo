# CLumo

[English (README.md)](README.md) | [日本語]

CLumo は、ESP32-C3・8x8 LED マトリクス（MAX7219）・2つのボタンでできた手のひらサイズの卓上ガジェットです。デジタルペットとして、ポモドーロタイマーとして、サイコロとして、あるいはスマホから絵を送れる小さなディスプレイとして、机の上に置いて楽しめます。

> 製品写真・デモ GIF は追って掲載します。

## 2つの使い方

CLumo には独立した2種類のファームウェアがあります。どちらかを選んで書き込んでください（あとから書き換えて切り替えられます）。

### 1. 単独版ファームウェア — アプリ不要

ボタン2つだけで完結します。Bluetooth もスマホもセットアップも不要です。

- **デジタルペット** — 餌やり・機嫌・アイドルアニメーション
- **ポモドーロタイマー** — 25分作業 / 5分休憩のサイクル
- **サイコロ** — ボタンひと押しでロール

白ボタン長押しでモードを切り替えます。現在のモードとペットの状態は電源を切っても保持されます。

### 2. アプリ協調版ファームウェア + Android アプリ

BLE で Android アプリ「**CLumo**」と連携し、より多機能に使えます。

- **ポモドーロ** — 作業・休憩時間をアプリから設定し、残り時間を64ピクセルで表示
- **タイマー** — `00:01`〜`59:59`を指定できる1回式カウントダウン。開始・一時停止・再開・キャンセルに対応
- **マイ表示** — アプリで描いた 8x8 パターンをデバイスに送信
- **オーディオビジュアライザ** — スマホの音に合わせてマトリクスが踊ります

## クイックスタート

1. **ブラウザからファームウェアを書き込む**（ツールのインストール不要）:
   **https://cespresso.github.io/CLumo/**
   単独版 / アプリ協調版を選び、USB でデバイスを接続してインストールを押すだけ。ESP Web Tools 製で、PC の Chrome / Edge（オプションで Android Chrome + USB-OTG）に対応しています。
2. **（協調版のみ）** [最新の GitHub Release](https://github.com/Cespresso/CLumo/releases/latest) から `clumo.apk` をダウンロードしてインストールし、デバイスとペアリング（パスキー `123456`）。

## リポジトリ構成

| パス | 内容 |
|---|---|
| [`standalone/`](standalone/) | 単独版ファームウェア（Rust・BLE なし）: ペット / ポモドーロ / サイコロ |
| [`companion/firmware/`](companion/firmware/) | 協調版ファームウェア（Rust + BLE）: ポモドーロ / タイマー / マイ表示 / ビジュアライザ |
| [`companion/android/`](companion/android/) | CLumo Android アプリ（Kotlin・Gradle） |
| [`installer/`](installer/) | ブラウザ書き込みページ（ESP Web Tools・GitHub Pages で配信） |
| [`hardware/`](hardware/) | 外装 3D モデル・配線図 — 近日公開 |

## ソースからのビルド

各サブプロジェクトは自己完結しており、詳細な手順はそれぞれの README にあります。

- **ファームウェア**（[`standalone/`](standalone/)・[`companion/firmware/`](companion/firmware/)）: Rust nightly、ESP-IDF v5.2.2、ターゲット `riscv32imc-esp-espidf`。`cargo build --release` でビルドし、`espflash` で書き込みます。
- **Android アプリ**（[`companion/android/`](companion/android/)）: 標準的な Gradle プロジェクトです。`./gradlew assembleDebug` または Android Studio で開いてください。

## ハードウェア

- MCU: ESP32-C3
- 表示: MAX7219 駆動の 8x8 LED マトリクス（SPI 接続）
- 入力: ボタン2つ（赤 / 白）

3D プリント可能な外装モデル・配線図・部品リストは [`hardware/`](hardware/) にて公開予定です。

## ライセンス

[MIT](LICENSE)

# CLumo

[English (README.md)](README.md) | [日本語]

CLumo は、ESP32-C3・8x8 LED マトリクス（MAX7219）・2つのボタンでできた手のひらサイズの卓上ガジェットです。デジタルペットとして、ポモドーロタイマーとして、サイコロとして、あるいはスマホから絵を送れる小さなディスプレイとして、机の上に置いて楽しめます。

![机の上に並んだ黄・緑・灰の CLumo 3 台。緑のものがハートを表示している](assets/clumo-cover.jpg)

## 2つの使い方

CLumo には独立した2種類のファームウェアがあります。どちらかを選んで書き込んでください。あとから書き換えれば切り替えられます。

### 1. 単独版ファームウェア: アプリ不要

ボタン2つだけで完結します。Bluetooth もスマホもセットアップも不要です。

- デジタルペット: 餌やり・機嫌・アイドルアニメーション
- ポモドーロタイマー: 25分作業 / 5分休憩のサイクル
- サイコロ: ボタンひと押しでロール

2 つのボタンのうち一方を長押しするとモードが切り替わります。モードごとの各ボタンの操作は [standalone/ の README](standalone/) にまとめてあります。動作中のポモドーロとペットの機嫌はモードを切り替えても続き、モード自体は電源を切っても保持されます。

### 2. アプリ協調版ファームウェア + Android アプリ

BLE で Android アプリ「CLumo」と連携し、より多機能に使えます。

- ポモドーロ: 作業・休憩時間をアプリから設定し、残り時間を64ピクセルで表示
- タイマー: `00:01`〜`59:59`を指定できる1回式カウントダウン。開始・一時停止・再開・キャンセルに対応
- マイ表示: アプリで描いた 8x8 パターンをデバイスに送信
- オーディオビジュアライザ: スマホの音に合わせてマトリクスが踊ります

接続前の本体の表示や、スマホが離れているときの動きは [companion/ の README](companion/) にまとめてあります。

## クイックスタート

1. ブラウザからファームウェアを書き込みます。ツールのインストールは不要です:
   **https://cespresso.github.io/CLumo/**
   単独版 / アプリ協調版を選び、USB でデバイスを接続してインストールを押すだけ。ESP Web Tools 製で、PC の Chrome / Edge に対応し、USB-OTG 経由なら Android の Chrome からも書き込めます。
2. 協調版のみ: [最新の GitHub Release](https://github.com/Cespresso/CLumo/releases/latest) から `clumo.apk` をダウンロードしてインストールし、パスキー `123456` でデバイスとペアリングします。

## リポジトリ構成

| パス | 内容 |
|---|---|
| [`standalone/`](standalone/) | 単独版ファームウェア（Rust・BLE なし）: ペット / ポモドーロ / サイコロ |
| [`companion/firmware/`](companion/firmware/) | 協調版ファームウェア（Rust + BLE）: ポモドーロ / タイマー / マイ表示 / ビジュアライザ |
| [`companion/android/`](companion/android/) | CLumo Android アプリ（Kotlin・Gradle） |
| [`installer/`](installer/) | ブラウザ書き込みページ（ESP Web Tools・GitHub Pages で配信） |
| [`hardware/`](hardware/) | 組み立てガイド・配線図・3D プリント用外装モデル |

## ソースからのビルド

各サブプロジェクトは自己完結しており、詳細な手順はそれぞれの README にあります。

- ファームウェア（[`standalone/`](standalone/)・[`companion/firmware/`](companion/firmware/)）: Rust nightly、ESP-IDF v5.2.2、ターゲット `riscv32imc-esp-espidf`。`cargo build --release` でビルドし、`espflash` で書き込みます。
- Android アプリ（[`companion/android/`](companion/android/)）: 標準的な Gradle プロジェクトです。`./gradlew assembleDebug` または Android Studio で開いてください。

## ハードウェア

- MCU: Seeed XIAO ESP32-C3
- 表示: MAX7219 駆動の 8x8 LED マトリクス（SPI 接続）
- 入力: タクトスイッチ 2 つ

外装は 3D プリントした 5 つの部品でできています。ネジと接着剤は使わず、XIAO は骨格にスナップフィットで留まり、配線はすべて JST XH コネクタ、はんだ付けはピンヘッダ 3 か所だけです。写真付きの[組み立てガイド](hardware/assembly/)に部品リストと配線図も入っています。3D モデルは [`hardware/models/`](hardware/models/) にあります。

## ライセンス

[MIT](LICENSE)

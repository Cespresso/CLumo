# CLumo Companion

CLumo をスマートフォンと組み合わせて使うためのセットです。

| ディレクトリ | 内容 |
|---|---|
| [`firmware/`](firmware/) | BLE 対応ファームウェア（Pomodoro / Timer / Display / Visualizer の4モード） |
| [`android/`](android/) | Android アプリ「CLumo」（スキャン・接続、ポモドーロ、タイマー、マイ表示エディタ、ビジュアライザ） |

## 使い方の流れ

1. `firmware/` を書き込む（またはインストーラページからブラウザで書き込み）
2. Android アプリをインストール（GitHub Releases の `clumo.apk`）
3. アプリで「CLumo を探す」→ 見つかったデバイスに接続（初回はパスキー `123456`）
4. アプリからモードを選び、ポモドーロ・タイマー・マイ表示・ビジュアライザを操作

## BLE プロトコル

プロトコル仕様（サービス / Characteristic / ペイロード形式）は
[`firmware/README.md`](firmware/README.md) を参照してください。
アプリとファームウェアはこの仕様を共有しています。

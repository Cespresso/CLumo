# AMS Volume Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans.

**Goal:** 既存の暗号化BLE接続でiPhoneのAMS音量を取得する。
**Architecture:** 純粋な通知解析・世代管理と、NimBLE FFI専用アダプタ。メイン処理からVolumeSessionへ反映。
**Tech Stack:** Rust, esp-idf-svc, NimBLE C API.
**Spec:** docs/superpowers/specs/2026-11-14-ams-volume-design.md

## Global Constraints
- HID操作、Android v2、NVS設定は維持。
- Cの非同期引数は完了まで有効。同時処理1件。切断・Service Changedで世代を更新。

## Review Focus
- 遅延コールバック・同じ接続ハンドルの再利用。
- 通知が購読書込の完了通知より先に到着する場合。
- AMS無しのAndroid接続で取得元を奪わない。
- NaN/範囲外/切り詰め/異なる属性の通知。
- 初期値未着、サービス消失、再公開。

## Tasks
- [x] 解析と通知無効化の失敗テスト→純粋モジュールを実装。
- [x] listener登録、サービス/特性/CCCD探索、購読、受信、タイムアウト・再探索を実装。
- [x] メイン処理へ接続しホストテスト・実機ビルド。
- [x] 独立レビュー、指摘の修正。
- [x] 書き込み、iPhone実機確認、Android回帰、記録。

## 検証・判断記録
- 未実装のamsモジュールを参照するテストの失敗を確認後に実装。既存42件＋AMS2件成功。
- 独立レビューで探索結果の世代確認と適用の間の競合を指摘。tick内の確認・キャッシュ変更・共通状態適用を同一ロック区間にし、startも保持した世代とrestartを検証するよう修正。NimBLE呼び出しはロック外。
- C APIの同期開始エラー時にはコールバックが無く、切断時には保留処理へ完了通知が届くことをローカルNimBLEソースで確認。
- ビルド・書き込み成功。iPhone確認中。Android Bluetoothと常時スキャンを検証用に一時停止しており、終了時に復元する。

- iPhone通常再接続で暗号化・AMS購読・初期値38%と31%→25%→31%の通知を確認。長押しの新規ペアリング待ち中は既存仕様でknown peerを拒否し、通常モードで解消。
- AndroidがADBから外れているため、AMS追加後のアプリ回帰とBluetooth／常時スキャンの復元は保留。
- ユーザーがiPhone音量・LEDの追従を確認。ログでもノブHID送信、AMS更新50%→44%、LED011への復帰を確認。
- Android再接続後、Bluetooth完全再起動・常時スキャン復元（両方1を読み戻し）。AMS版でvolumeV2AndReconnect成功14.819秒、ログで境界・失効・復帰を照合。通常アプリConnectedと実音量25%も確認。Rust44件/fmt、Android unit/ktlint成功。詳細はREADME。

# Volume sources Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 音量取得・接続管理を表示から分離し、Android経路をv2へ更新する。
**Architecture:** 型付き音量状態、接続世代、取得元固有の有効性判定。揮発状態はメインループだけが所有する。
**Tech Stack:** Rust / esp32-nimble 0.11.1 / Kotlin Android。
**Spec:** docs/superpowers/specs/2026-11-14-volume-sources-design.md

## Global Constraints
- 既存ブランチ上で実装。旧通信との互換不要。AMS実通信・実機書き込みはこの段階に含めない。
- 保存設定とHID操作は維持。

## Review Focus
- 再接続で同じ接続ハンドルを再利用しても旧通知を拒否する。
- 無音0、不明、不正な値を区別する。
- Androidの受信停止で失効、同じ音量の報告で回復する。
- AMSは時間経過で失効せず、購読解除で失効する。
- 切断で表示の揮発情報を消すが設定は保持する。

## Task 1: 共通状態と取得器
- [x] firmware/tests/volume_sources.rsで上記の失敗テストを書く。
- [x] firmware/src/volume.rsにPercent、SessionId、Source、Availability、VolumeSessionとAndroidVolumeを実装する。
- [x] firmware/src/lib.rsからVolumeコマンドと表示TTLを除き、型付き値とclear_transientを追加する。
- [x] cargo test --lockedで全件成功を確認。

## Task 2: デバイス統合とAndroid
- [x] device.rsの揮発状態をmainへ移し、世代付きメールボックスを導入。設定は別の共有構造体。
- [x] Androidのプロトコルテストをv2・nullable音量へ更新し失敗確認。
- [x] Androidの取得器を独立させ、500msの状態報告をv2エンコードする。
- [x] Rust実機ビルド、Android unit test/assemble、fmtと差分を確認。

## Task 3: 検証と記録
- [x] READMEに現仕様と検証結果、未検証のAMS/実機を記録。
- [x] 独立レビューの重要指摘を修正し、必要なテストを再実行する。

## 検証記録
- Rust: 新しい型と接続世代テストの未実装エラーを確認後に実装。既存36件＋新規6件成功。fmt、ESP32-C3 releaseビルド成功。
- Android: v2識別・不明値テスト3件の失敗を確認してから実装。全テストと静的解析の最終結果はREADMEへ記録する。
- 独立レビュー: v2整合性、期限、ロック順、切断時の破棄を確認。新規の確実な重大不具合なし。
- Final: fixed HID送信の旧セッション競合 — hid_send_rechecks_session_even_when_handle_is_reused RED→GREEN。送信時にcharacteristic→connectionの順でロックし、世代・準備状態を確認したまま送信する。NimBLEのGAPコールバックはhostロック外で呼ばれることをローカルソースで確認。
- Ruling: 生存確認と値は意味を分けつつ一つの原子的スナップショットとして500ms周期で報告する。別パケットにした際の片方欠落や再同期処理を避ける。通信量は値が一定でも維持する。
- 実機への展開、AMSの探索・購読、コミットは今回実施しない。

- 最終検証: Rust42件／Android292件成功、fmt／実機ビルド／APK／androidTestコンパイル／ktlint成功。lintDebugのみ未変更依存の更新通知5件で終了（README参照）。
- Final: Ruling: Gradle/AGP/Compose/Core/DataStoreの更新は音量変更の検証範囲外として実施しない。代償は既存lintDebugが依存更新通知で失敗すること。アプリコードのLint指摘はゼロ。

## 続行: ユーザー依頼による実機展開
- 2026-11-14: 両側更新と実機検証をユーザーが指示。正式版1,344,336バイト書き込み・ハッシュ一致、Android更新済み。
- volumeV2AndReconnect成功（15.024秒）。初回失敗は他端末接続とAndroid接続待ち残留であり、疑ったロック変更は不要だったため取り消した。
- 詳細・通常アプリ確認はheadphone-stand/README.mdを参照。AMS実通信・コミットは未実施。

# Stand Setup Mode Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. This is an authorized hardware trial.

**Goal:** 前曲＋次曲3秒長押しでBLE通常モードとWi-Fi確認モードを切り替える。
**Architecture:** ホストテスト可能な入力判定と、NVSの一回限りの起動先指定による無線モード切り替え。
**Tech Stack:** Rust / ESP-IDF 5.2.2 / esp-idf-svc 0.51 / NimBLE。
**Spec:** docs/superpowers/specs/2026-11-14-stand-setup-mode-design.md

## Global Constraints
- 自動復帰・保存して終了は設けない。ペアリング情報は保持。
- 既存作業ブランチで継続。描画編集は次の段階。

## Review Focus
- 起動時押しっぱなし、3秒未満キャンセル、短押しの取りこぼし、長押し連続発火、無線モード排他。

## Task 1: 入力判定
- [x] src/setup_buttons.rs に SetupButtons::update([bool;2], u64) -> Option<SetupEvent> と progress を追加。遅延150ms、長押し3000ms。
- [x] ホストテストで単独短押し・同時押し・キャンセル・起動時抑止を先に検証して実装。

## Task 2: 設定モード
- [x] device.rs の起動時分岐、setup.rs のSoftAP／HTTP確認ページ、hardware.rsのPeripherals受け渡しを実装。
- [x] READMEに操作、SSID、URL、試作範囲を記載。
- [x] 全ホストテスト、fmt、実機ビルドを実行し、接続ESP32へ書き込み。物理ボタン操作はユーザーに確認を依頼。

## 実行記録
- 入力テストを失敗確認後に実装。連打の上書き問題も回帰テストで再現して修正。全24件成功、fmt/diffチェック成功。
- ESP32-C3ビルド成功、1,311,296バイトを書き込み、ハッシュ一致。USBログで通常起動と登録済み端末からのBLE接続を確認。
- 設定モードへの物理切り替えはユーザー確認済み、ログでSoftAP起動確認。ページ表示・復帰はユーザー回答待ち。編集画面は範囲外。
- 実機ログでAPの既定IPが192.168.71.1と判明し、READMEとログ表示を修正。修正版ビルド1,313,952バイトは成功、接続確認を妨げないよう再書き込み待ち。
- URL修正版を書き込み済み。Androidで設定AP接続・DHCP取得・HTTP200/HTML全文取得を確認。Macの失敗時状態は未確定だが調査時は別サブネット。検証用AndroidのAP登録38のみ解除済み。

## Task 3: Captive portal
- [x] DNS応答を純粋関数としてTDD。A/AAAA、EDNS追加レコード、壊れた名前・圧縮参照・複数質問の境界を確認。
- [x] APの固定IPとDHCP DNS配布、非同期待ちなしUDP53、HTTPワイルドカード302を追加。
- [x] ホスト全テスト、fmt、実機ビルド、書き込み。AndroidでDNS・HTTP接続確認への応答、可能ならOSのポータル検出を確認。

- ポータル対応: ホスト28件、fmt、実機ビルド成功。レビューで重要問題なし。1,320,160バイトの書き込み・ハッシュ一致。ユーザーの設定モード切り替え待ち。
- ADBが no devices/emulators found のため、ポータル版の自動案内実機確認はユーザー端末で確認待ち。書き込み済み、コミットは未実施。

- Android実機でCAPTIVE_PORTAL判定、DHCP DNS、名前解決、302/200を確認。標準WebViewで512バイト上限によるヘッダーエラーを再現し、2048へ変更・生成設定確認・ビルド・書き込み（1,320,272バイト、ハッシュ一致）。再試験でWi-Fi設定のSign in to networkから確認本文の表示に成功。iOS/macOS未検証。

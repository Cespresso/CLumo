# Stand Animation Editor Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans.

**Goal:** プリセットと最大8コマのブラウザ編集を実装し実機へ書き込む。
**Architecture:** Settingsの後方互換拡張、純粋な待機再生、設定専用HTTP APIと単一HTML。
**Tech Stack:** Rust ESP-IDF、HTML/CSS/JavaScript（外部依存なし）。
**Spec:** docs/superpowers/specs/2026-11-14-stand-animation-editor-design.md

## Global Constraints
- 保存で設定モードを終了しない。自動終了なし。旧GATT形式維持。
- 1〜8コマ、100〜2000ms、明るさ0〜15。

## Review Focus
- 旧設定移行、不正データ、Android上書き、未保存プレビュー、保存失敗・連続送信。

## Task 1: 保存形式と再生
- [x] ホストテストで旧10バイト、新形式、境界・不正値、再生ループ、Android上書き、操作優先を先に検証する。
- [x] SettingsにAnimationとidle_frameを追加。NVS読み込みバッファを拡張。

## Task 2: 編集画面とAPI
- [x] setup.rsにGET /api/settingsとPOST /api/preview,/api/saveを追加。固定長検証、保存失敗時の状態保持。
- [x] editor.htmlにプリセット、8×8入力、8コマ管理、再生・速度・明るさ、実機プレビューと保存を追加。
- [x] ホストテスト、fmt、実機ビルド、UI確認とレビューを行い書き込み。
- [x] 実機でAPI保存・再取得・画面表示を確認し、検証結果をREADMEに記録。

## 実行記録
- 32件のホストテスト、fmt、差分、実機ビルド成功。レビューで重要問題なし。
- ローカル模擬APIのブラウザで8コマ選択、ドット編集、保存・再読み込みと見た目を確認。
- 1,342,144バイト書き込み・ハッシュ一致。実機APIでHTML一致、プレビュー非永続、入力拒否、8コマ保存・読み戻しを確認し元の設定へ復元。
- 再起動後保持・物理表示・Androidアプリ上書きの実機確認は未実施。ユーザーが操作できる状態で引き渡し。

# ヘッドホンスタンドの機能紹介資料

2026-11-14。依頼元チャット: `01a1d7a3-657a-7543-acfd-0d169aa82d4a`（nothing筐体を色別モデル化）。
依頼: Androidアプリあり／なし／iOSの機能比較表（日英）、アプリとWi-Fi設定画面のスクリーンショット、接続・配信条件と確認範囲。

## 操作と表示の図解

別セッションで整理した日英図解をCLumoへ取り込み済み。

- [日本語図解](diagrams/controls.ja.png) / [English diagram](diagrams/controls.en.png)
- [掲載プレビュー（日本語）](index.ja.html) / [English preview](index.en.html)
- [編集元・出典・再生成方法](diagrams/README.md)

掲載順は図解 → 対応環境の比較 → 接続画面 → 表示編集 → 操作割り当て。トップREADMEにも図解を掲載。

## 機能比較表

- [日本語](features.ja.md)
- [English](features.md)

通常操作の初期値は左「前へ」、右「次へ」、ノブ1回「再生／一時停止」、2回「次へ」、3回「前へ」、回転「音量」。左右ボタンとノブ操作は設定変更可能。システム操作のノブ3秒ペアリング・左右同時3秒Wi-Fi切り替えは固定。

iPhoneはAMS経由で実音量LEDが追従するため「アプリがないのでLED追従不可」と記載しない。Androidのアプリなしでは実音量を取得できず、ノブ操作時のシーケンス表示になる。ビジュアライザはAndroidアプリの音声取得権限と配信が必要。

## 採用スクリーンショットとキャプション

全画像はCMF Phone 1で撮影した1080×2400の未加工PNG。英語UI。Android版は今回撮り直し、Web版は同日の実機検証中に撮影した最新版。OSバー・ブラウザバーを含む。端末名の52F8は実機固有の識別子。

| 画像 | 日本語キャプション | English caption |
| --- | --- | --- |
| [Android接続画面](screenshots/android-connected.png) | Androidアプリで接続状態を確認し、明るさと音声ビジュアライザを調整。 | Check the connection and adjust brightness and the audio visualizer in the Android app. |
| [表示エディター](screenshots/android-display-editor.png) | プリセットから選ぶか、8×8のドットを編集。静止画も最大8コマのアニメーションも作成可能。 | Choose a preset or edit the 8×8 pixels to create a still image or an animation of up to eight frames. |
| [8コマとプレビュー](screenshots/android-display-preview.png) | コマの並べ替えと画面内プレビュー。保存するまで本体表示は変わりません。 | Reorder frames and preview the animation on screen before saving it to the stand. |
| [Android操作割り当て](screenshots/android-control-assignments.png) | 部品を選んで操作を変更。初期割り当てへの復元にも対応。 | Select a control to change its assignment or restore the defaults. |
| [Web操作割り当て](screenshots/web-control-assignments.png) | Wi-Fi設定ページで操作を一覧編集し、本体へまとめて保存。 | Edit control assignments in the Wi-Fi setup page and save them to the stand. |

アプリの筐体イラスト内の3LEDは装飾であり、実音量の数値表示ではない。実音量の追従を説明する場合は実機LEDの写真／動画と比較表を使う。エディターのオレンジ色はUI上の描画色であり、実物マトリクスLEDの発光色指定ではない。

Webの「No internet connection」はインターネットへ中継しない設定用APの正常な状態。ブラウザ画面はAndroid Chromeで確認したもので、iPhoneでの撮影／動作確認の証拠として使わない。

## 保存・確認状況

- ソフトウェア: `5ca707a`。共有表示エディター、外観対応、操作割り当て、ペアリング復旧、Siri保持時間などを収録。
- README・配線図・3MFなど: `fc95609`。
- Rustホスト50件、Android単体297件、Android ktlint／ビルド成功。
- BLE実機: 表示1／8コマの保存復元、操作割り当て全コード・回転・初期値復元・再接続保持を確認。
- Web実機: HTML一致、割り当て保存・読み戻し・不正データ拒否を確認。画面からもMute保存とRestore defaults→Save assignmentsを確認。
- iPhone: 音量操作・AMS・LED追従・Mute・Siri起動をユーザー確認。Androidの音声アシスタント、早送り／巻き戻しの実動作、iPhoneブラウザ編集は未確認。
- 今回の撮影では設定を変更していない。本体は通常BluetoothモードでAndroid接続済み。

## 以前の画像について

`../review/shared-editor/`と`../review/setup-english/`は変更途中のUIを含む検証履歴。新規公開資料にはこのページの`screenshots/`を優先する。Webの表示編集部分はアプリと同一HTMLだが、Android画面をWeb撮影と表記しない。

## 返答用要約

日英の3環境比較表と、最新Android UI／Web設定の5枚のスクリーンショットを用意しました。上記表のキャプションを掲載に利用できます。Androidアプリの有無による実音量LED・ビジュアライザの差、iPhoneのAMS対応、操作割り当てと初期値復元を反映しています。確認済みと未検証を分けて記載してください。

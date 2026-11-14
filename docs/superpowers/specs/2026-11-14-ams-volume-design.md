# AMS音量取得

承認済みの共通音量基盤へiPhone用AMS取得器を追加する。HID操作経路は維持し、AMS Remote Commandは使用しない。Androidのv2通信と保存設定も維持する。

選択: NimBLE標準GAP listenerとGATTクライアントC APIを専用モジュールへ隔離する。Rustライブラリ全体のforkや二重BLE接続は不要。既存の暗号化接続ハンドルでサービス探索、Service Changedのindicate購読、Entity Updateのnotify購読、Player Volume [0,2]登録を順番に実行する。

AMS通知を0..100のPercentに変換し、同じVolumeSessionへ渡す。AMSが実際に見つかるまでは取得元を確定しない。Androidが採用済みなら探索を止める。AMSの初期値待ちに上限を設け、サービス消失・不正通知・GATTエラーで不明に戻して再探索する。無変更の音量値にTTLは設けない。

非同期処理には接続世代と探索世代を付ける。切断、暗号化喪失、Service Changedで旧通知と処理結果を無効化。Cコールバックの引数は処理終了まで保持し、タイムアウトだけで解放しない。同時GATT操作は1件、通知は最新1件。BLEコールバックは描画・共通状態を直接変更せず、メイン処理が適用する。Bluetooth API呼び出し中はAMS内部ロックを保持しない。

AMS未提供時はService Changedで再探索し、補助として30秒周期で再探索。HIDの利用可否から独立する。iPhoneでの音量追従とAndroid回帰を実機確認する。

仕様: https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Specification/Specification.html
音量書式: https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Appendix/Appendix.html

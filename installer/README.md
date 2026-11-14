# ブラウザインストーラー

従来CLumoの単独版・協調版と、ヘッドホンスタンド専用版を選択できます。
スタンド版への直接リンクは `https://cespresso.github.io/CLumo/?fw=headphone-stand`。
`lang=ja` / `lang=en`、`v=pc` / `v=mobile`で言語と書き込み方式を指定できます。

## リリースから配信まで

1. `v*`タグで`Build Release`が3種類のESP32-C3ファームウェアと署名済みAndroid APKをビルドします。Android署名には既存の`android-release`環境の設定が必要です。
2. 全ビルド成功後、4ファイルを同じGitHub Releaseへ添付します。ファームウェアはブートローダー・パーティション表を含む4MB結合イメージです。
3. `Deploy Installer`はリリースワークフローの成功後、または`main`更新時に動きます。Pagesのソースは常に`main`です。
4. `scripts/prepare_installer.py`が最新の正式リリースを一度選び、そのタグから各バイナリを取得します。マニフェストのバージョンも同じタグにそろえ、Pagesと同一オリジンから配信します。

該当ファイルがない機種はインストールを無効にします。スタンド版には同じリリースの`clumo.apk`も必要です。古いリリースしかない場合、従来2種類は引き続き使えます。ダウンロード失敗時はPagesの更新を失敗させ、不完全な配布物を公開しません。

この変更をマージするだけではスタンド用バイナリは公開されません。対応するタグのリリースを作り、ビルドとPages配信が成功する必要があります。

## 検証

リポジトリルートから:

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
node --test scripts/test_installer_ui.cjs
```

実リリースを使う確認は、作業コピー内のJSONを上書きしないよう一時ディレクトリで行います。

```sh
cp -R installer /tmp/clumo-installer-check
python3 scripts/prepare_installer.py --repo Cespresso/CLumo --directory /tmp/clumo-installer-check
python3 -m http.server 8080 --directory /tmp/clumo-installer-check
```

ブラウザから`http://localhost:8080/`を開きます。実際のUSB書き込みには対応ブラウザと実機が必要です。GitHub上のビルド・公開はローカルテストと別に確認してください。

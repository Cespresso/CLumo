# CLumo Android アプリ

[English (README.md)](README.md) | [日本語]

アプリ協調版ファームウェア用の Android アプリです。何ができるか、本体がどう動くかは
[協調版の README](../README.ja.md) にまとめてあります。

## 必要なもの

- Android 8.0 以上で Bluetooth Low Energy を備えたスマホ
- JDK 17。Android Studio に同梱されています。なければ JDK だけ入れれば、
  残りは Gradle ラッパーが取ってきます。

## ビルド

```bash
./gradlew assembleDebug
```

APK は `app/build/outputs/apk/debug/` にできます。デバッグビルドはパッケージ名が
別なので、リリース版と置き換わらず並んでインストールされます。

## リリースビルド

リリースビルドは、`CLUMO_KEYSTORE_PATH`、`CLUMO_KEYSTORE_PASSWORD`、`CLUMO_KEY_ALIAS`、
`CLUMO_KEY_PASSWORD` の 4 つの環境変数で指す keystore で署名します。どれかが欠けていると、
デバッグ鍵で代用せずにビルドが失敗します。署名済みの APK は
[GitHub Releases](https://github.com/Cespresso/CLumo/releases/latest) に `clumo.apk` として
置いています。

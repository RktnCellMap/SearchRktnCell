# 楽天電測【非公式】

楽天モバイルの基地局を探す（電測する）ための Android アプリです。
接続中のセルの情報を地図と一緒に表示し、移動しながら基地局の位置を絞り込むのに使います。

楽天モバイル株式会社とは無関係の、個人による非公式アプリです。

- アプリの紹介ページ: https://cellmap.rukihena.com/SearchRktnCell/

## 主な機能

- 接続中セルの情報表示（eNB-LCID、PCI、TAC、バンド、RSRP、TA など）
- Google マップ上への現在地と電測軌跡のプロット
- TA・RSRP をもとにした「最良電測ポイント」の記録
- Google マイマップ（KML）を読み込み、登録済みの基地局を地図に表示
- MLS データを読み込み、セルのおおよその位置を表示
- 目的地を指定する簡易ナビ、ノースアップ／ヘディングアップ切り替え
- 設定のエクスポート／インポート

## ビルド方法

開発には **Android Studio Giraffe | 2022.3.1 Patch 1** を用いています。

1. このリポジトリを clone し、Android Studio で開きます。
2. Google Cloud で「Maps SDK for Android」を有効にした API キーを用意します。
3. プロジェクト直下に `local.properties` を作り、次の 2 行を書きます（すでにある場合は足りない行を追加します）。

   ```properties
   sdk.dir=C:/Users/ユーザー名/AppData/Local/Android/Sdk
   MAPS_API_KEY=取得したAPIキー
   ```

   `sdk.dir` は Android SDK の場所です。Android Studio の Settings → Languages & Frameworks → Android SDK の「Android SDK Location」で確認できます。
   Windows のパスは、上の例のように区切りを `/` で書きます。

4. Gradle Sync をやり直し、ビルドして実機にインストールします。

`local.properties` は git の管理対象外です。API キーをリポジトリに含めないでください。
キーを設定しなくてもビルドはできますが、地図は表示されません。

## 動作環境

- Android 8.0（API 26）以上
- 位置情報と電話の状態（セル情報）へのアクセス許可が必要です

## ライセンス

[MIT License](LICENSE) です。
同梱フォント Abel は SIL Open Font License 1.1 です（[licenses/](licenses/) を参照）。

## 作者

X: [@RktnCellMap](https://x.com/RktnCellMap)

トリヨ音声入力 Android版 開発プロジェクト

重要：このZIPはAPKではなく、Android StudioでAPKをビルドするためのソースプロジェクトです。
この実行環境にはAndroid SDK / Gradleビルド環境がないため、APKの生成・署名はまだ行っていません。

実装済みの試作機能：
- Android標準の日本語音声認識（認識サービスとネット接続に依存）
- 認識結果の文章編集
- TXT保存
- DOCX形式の簡易Wordファイル生成
- 音声録音（アプリ専用保存領域）
- ChatGPT等へ文章を共有して修正依頼を送る機能

未完成・次段階：
- AI修正結果をアプリに自動で戻す機能（安全なサーバー/API連携が必要）
- 保存ファイルをAndroidの共有・ダウンロード領域へ出す仕上げ
- 長時間連続認識の自動再開・結合
- Windowsとの履歴同期
- 実機での動作試験とAPK署名

ビルドするにはAndroid StudioとAndroid SDKが必要です。app/build.gradleでcompileSdk 35を使用します。


追加：GitHub Actions用のAPKビルド設定
.github/workflows/android-apk.yml を追加しました。ただしGradle Wrapper（gradlew と gradle/wrapper/gradle-wrapper.jar等）がまだ含まれていないため、このままではGitHub Actionsビルドは開始できません。
Android StudioでGradle Wrapperを生成するか、Gradleがインストールされた環境で `gradle wrapper` を実行する必要があります。
また、このプロジェクトは試作段階であり、実機テスト・正式署名・AI自動連携は未完了です。

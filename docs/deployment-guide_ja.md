# DeepCover デプロイガイド

[中文](deployment-guide.md) | [English](deployment-guide_en.md) | **日本語** | [Francais](deployment-guide_fr.md) | [Portugues](deployment-guide_pt.md) | [Русский](deployment-guide_ru.md)

## 環境要件

| コンポーネント | バージョン | 備考 |
|---------------|-----------|------|
| Java | 8+ | 推奨: 1.8.0_202+ |
| Maven | 3.5+ | エージェントのビルド用 |
| JVM Sandbox | 1.4.0 | バイトコード拡張フレームワーク |
| Servlet コンテナ | 3.0+ | Tomcat, Jetty など |

## ステップ 1: DeepCover エージェントのビルド

```bash
git clone https://github.com/xiaobin1187-git/deepcover.git
cd deepcover
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

出力: `target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`

## ステップ 2: JVM Sandbox のインストール

[公式リポジトリ](https://github.com/alibaba/jvm-sandbox)から JVM Sandbox をダウンロードして展開:

```
sandbox/
├── bin/
│   └── sandbox.sh
├── cfg/
│   └── sandbox.properties
├── lib/
│   ├── sandbox-agent.jar    # javaagent JAR
│   └── sandbox-spy.jar
└── sandbox-module/          # DeepCover モジュールの配置先
```

## ステップ 3: DeepCover モジュールのデプロイ

```bash
# ビルド済みエージェントを Sandbox モジュールディレクトリにコピー
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/
```

## ステップ 4: ターゲットアプリケーションの設定

アプリケーションの起動スクリプトに JVM パラメータを追加:

### Tomcat

`bin/setenv.sh`（または `catalina.sh`）を編集:

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=your-app-name \
  -Denv=test \
  -Ddeepcover.env=test \
  -Dbranch=master"
```

### Spring Boot (java -jar)

```bash
java -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
     -Dapp.name=your-app-name \
     -Denv=test \
     -Ddeepcover.env=test \
     -jar your-app.jar
```

### 重要: SkyWalking との互換性

SkyWalking APM を使用する場合、DeepCover エージェントパラメータは SkyWalking より**前に**配置してください:

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \     # DeepCover を先に
-javaagent:/path/to/skywalking-agent.jar \               # SkyWalking を後に
```

## ステップ 5: deepcover.properties の設定

設定テンプレートをコピーして編集:

```bash
cp src/main/resources/deepcover.properties.example \
   src/main/resources/deepcover.properties
```

詳細なパラメータ説明は[設定ガイド](configuration-guide_ja.md)を参照してください。

## ステップ 6: インストールの確認

### モジュールのロード確認

```bash
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

モジュールのロード完了メッセージが表示されます。

### メトリクスエンドポイントの確認

```bash
curl "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/metrics"
```

ランタイム統計の JSON レスポンスが返されます。

## ステップ 7: モジュールライフサイクルの制御

```bash
# モジュールの有効化
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# モジュールの無効化（再有効化可能）
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# モジュールの完全アンロード（再有効には再起動が必要）
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=your-app-name"
```

## トラブルシューティング

### モジュールのロードに失敗する場合

- `sandbox-agent.jar` のパスが正しいことを確認
- `app.name` と `env` JVM パラメータが設定されていることを確認
- `~/sandbox/sandbox.log` でエラー詳細を確認

### カバレッジデータが収集されない場合

- `packageName` の正規表現がアプリケーションパッケージにマッチすることを確認
- `deepcover.properties` の `env` セクションが正しいことを確認
- データセンターまたは Kafka にアプリケーションホストからアクセス可能か確認

### パフォーマンスへの影響が大きい場合

- `sampleRate` を下げる（例: `5000` で 50%）
- `limitCodeMethodSize` と `limitCodeMethodLineSize` を下げる
- `/metrics` エンドポイントでサーキットブレーカーの動作を確認

### キュー満了の警告

- `queueSize` を増やす（デフォルト: 100）
- `queueNum` を増やす（デフォルト: 1）
- `sampleRate` を下げてデータ量を減らす

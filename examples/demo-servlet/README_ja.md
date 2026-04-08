# DeepCover デモ - Servlet アプリケーション

[中文](README.md) | [English](README_EN.md) | **日本語** | [Francais](README_fr.md) | [Portugues](README_pt.md) | [Русский](README_ru.md)

DeepCover がコードカバレッジデータをどのように収集するかを示すシンプルな Web アプリケーションです。

## 前提条件

- Java 8 以上
- Maven 3.5 以上
- Tomcat 8 以上（または任意の Servlet コンテナ）
- [DeepCover Agent](../../) がビルド済みであること

## クイックスタート

### 1. デモをビルドする

```bash
cd examples/demo-servlet
mvn clean package
```

`target/demo-servlet.war` が生成されます。

### 2. DeepCover エージェントで JVM を設定する

Tomcat の `catalina.sh`（または `setenv.sh`）に以下を追加します：

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=demo-servlet \
  -Denv=test \
  -Ddeepcover.env=test"
```

### 3. デプロイしてテストする

```bash
# WAR を Tomcat にコピー
cp target/demo-servlet.war /path/to/tomcat/webapps/

# Tomcat を起動
/path/to/tomcat/bin/startup.sh

# エンドポイントをテスト
curl "http://localhost:8080/demo-servlet/user?action=list"
curl "http://localhost:8080/demo-servlet/user?action=get&id=1"
curl "http://localhost:8080/demo-servlet/user?action=create&name=Bob&email=bob@example.com"
curl "http://localhost:8080/demo-servlet/user?action=delete&id=1"
```

### 4. メトリクスを確認する

```bash
# DeepCover ランタイムメトリクスを表示
curl "http://localhost:port/sandbox/default/module/http/deepcover/metrics"
```

## DeepCover が収集する内容

HTTP リクエストが `UserServlet.service()` に到達すると、DeepCover は以下を行います：

1. **リクエストのトレース** - HTTP メソッド、URL、traceId を記録します
2. **メソッド呼び出しの計装** - `io.deepcover.examples.demo.*` パッケージ内のすべてのメソッド呼び出しを追跡します
3. **行番号の収集** - `UserService` で実行されたコード行を記録します
4. **データの送信** - カバレッジデータを設定されたデータセンターまたは Kafka トピックに送信します

## カバレッジデータの例

`GET /user?action=get&id=1` へのリクエストに対して、DeepCover は以下を収集します：

- クラス: `io.deepcover.examples.demo.controller.UserServlet`
- メソッド: `service` - 実行された行: 24, 25, 26, 29, 34
- クラス: `io.deepcover.examples.demo.service.UserService`
- メソッド: `getUser` - 実行された行: 22, 23, 26, 27

このデータは、HTTP リクエストごとの行レベルのコードカバレッジを計算するために使用できます。

## 設定

`deepcover.properties` に正しい設定が含まれていることを確認してください：

```properties
# 収集するパッケージパターン
test.packageName=io\\.deepcover\\.examples\\.demo\\..*
```

または、設定センターで `packageName=io\.deepcover\.examples\.demo\..*` を設定してください。

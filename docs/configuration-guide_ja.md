# DeepCover 設定ガイド

[中文](configuration-guide.md) | [English](configuration-guide_en.md) | **日本語** | [Francais](configuration-guide_fr.md) | [Portugues](configuration-guide_pt.md) | [Русский](configuration-guide_ru.md)

## 設定方法

DeepCover は3つの設定方法をサポートしています:

1. **設定ファイル** - `deepcover.properties`（静的、起動時に読み込み）
2. **設定センター** - `deepcover-brain`（動的、定期的に取得）
3. **HTTPコマンド** - `syncConfig`（オンデマンド、Sandbox HTTP API経由）

## 設定ファイル

### 場所

```
src/main/resources/deepcover.properties
```

### フォーマット

ファイルは環境ごとに整理されています:

```properties
# テスト環境
test.dataCenterAddr=http://127.0.0.1:8080/api/collect
test.configCenterAddr=http://127.0.0.1:8080
test.KAFKA_BOOTSTRAP_SERVERS=localhost:9092
test.KAFKA_TOPIC=deepcover-collection-code-info

# 本番前環境
pre.dataCenterAddr=http://pre-dc.example.com:8080/api/collect
pre.configCenterAddr=http://pre-dc.example.com:8080
pre.KAFKA_BOOTSTRAP_SERVERS=pre-kafka.example.com:9092
pre.KAFKA_TOPIC=deepcover-collection-code-info

# 本番環境
pro.dataCenterAddr=http://dc.example.com:8080/api/collect
pro.configCenterAddr=http://dc.example.com:8080
pro.KAFKA_BOOTSTRAP_SERVERS=kafka.example.com:9092
pro.KAFKA_TOPIC=deepcover-collection-code-info

# 共通設定
getDeepCoverInfo=/deepcover-brain/deepcover/info
reportServerInfo=/deepcover-brain/deepcover/report
```

アクティブな環境は JVM パラメータ `-Ddeepcover.env=test` または `-Denv=test` で決定されます。

## パラメータ一覧

### コアパラメータ

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `serviceName` | String | - | アプリケーション名（`-Dapp.name` で設定） |
| `env` | String | - | 環境: test/pre/pro（`-Denv` または `-Ddeepcover.env` で設定） |
| `branch` | String | `master` | Git ブランチ名（`-Dbranch` で設定） |

### 収集スコープ

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `packageName` | Regex | - | 計測対象のパッケージパターン。この正規表現にマッチするクラスのみ監視されます。 |
| `ignoreClasses` | Regex(s) | - | 除外するクラスパターン（セミコロン区切り）。正規表現対応。 |
| `ignoreMethods` | Regex(s) | - | 除外するメソッドパターン（セミコロン区切り）。 |
| `ignoreUrls` | String(s) | - | 除外するURLパス（セミコロン区切り）。例: `/health;/metrics` |
| `ignoreAnnos` | String(s) | - | 除外するアノテーション。 |

### サンプリング

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `sampleRate` | Integer | `10000` | サンプリングレート。`10000` = 100%、`5000` = 50%、`100` = 1%。traceId ハッシュに基づく決定的サンプリング。 |

### パフォーマンス制限

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `limitCodeMethodSize` | Integer | `500` | HTTPリクエストあたりの最大収集メソッドノード数。超過するとそのリクエストのデータは破棄されます。 |
| `limitCodeMethodLineSize` | Integer | `500` | メソッドあたりの最大行収集回数。超過するとそのメソッドの行収集が停止します。 |

### データエクスポート

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `sendDataCenterType` | Integer | `1` | エクスポート方式: `1` = HTTP、`2` = Kafka |
| `dataCenterAddr` | URL | - | カバレッジデータ送信先のデータセンターHTTPエンドポイント |
| `KAFKA_BOOTSTRAP_SERVERS` | String | - | Kafka クラスタのブートストラップサーバー |
| `KAFKA_TOPIC` | String | - | カバレッジデータ送信先の Kafka トピック |

### キュー設定

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `queueNum` | Integer | `1` | 内部非同期キューの数 |
| `queueSize` | Integer | `100` | キューあたりの最大要素数（ArrayBlockingQueue容量） |
| `queueMsgSize` | Integer | `50` | キュードレイン操作の最大バッチサイズ |
| `queueRecycleTime` | Integer | `10` | バッチ間のコンシューマスレッドスリープ時間（秒） |

### サーキットブレーカー

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `exceptionThreshold` | Integer | `10` | サーキットブレーカーをトリガーする例外数 |
| `exceptionCalcTime` | Integer | `1` | 例外カウントの時間窓（分） |
| `exceptionPauseTime` | Integer | `5` | サーキットブレーカー発動後の一時停止時間（秒） |

### レポート

| パラメータ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `reportPeriod` | Integer | `10` | サーバー情報報告と設定ポーリングの間隔（秒） |

## 動的設定

### syncConfig コマンド経由

すべてのパラメータは再起動なしでランタイム更新可能:

```bash
curl -X POST "http://sandbox-host:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20&limitCodeMethodSize=300"
```

### 設定センター経由

エージェントは設定センターを定期的にポーリングします（`reportPeriod` 間隔）。設定バージョンが上がると、全パラメータが自動的にリロードされます。

## モニタリング

メトリクスエンドポイントでエージェントのステータスを確認:

```bash
curl "http://sandbox-host:port/sandbox/default/module/http/deepcover/metrics"
```

レスポンス例:

```json
{
  "serviceName": "my-app",
  "env": "test",
  "uptimeSeconds": 3600,
  "configVersion": 5,
  "sampleRate": 5000,
  "sendType": "Kafka",
  "totalRequests": 15000,
  "collectedRequests": 7500,
  "droppedRequests": 50,
  "totalLinesCollected": 120000,
  "methodThresholdReached": 10,
  "sendSuccess": 7400,
  "sendFailed": 50,
  "queueOfferFailed": 30,
  "circuitBreakerTripped": 0,
  "circuitBreakerPaused": false
}
```

## 推奨本番設定

```properties
sampleRate=1000              # 10% サンプリング
limitCodeMethodSize=300      # 適度なメソッド制限
limitCodeMethodLineSize=300  # 適度な行制限
exceptionThreshold=5         # 控えめなサーキットブレーカー
exceptionCalcTime=1          # 1分ウィンドウ
exceptionPauseTime=10        # 10秒一時停止
queueNum=2                   # 複数キュー
queueSize=200                # 大きなキューキャパシティ
reportPeriod=30              # 低頻度レポート
```

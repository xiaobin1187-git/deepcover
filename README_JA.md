<img src="docs/assets/logo.svg" alt="DeepCover Logo" width="128" height="128" align="right">
# DeepCover - フルチェーンプレシジョン分析収集エージェント

[中文](README.md) | [English](README_EN.md) | **日本語** | [Francais](README_FR.md) | [Portugues](README_PT.md) | [Русский](README_RU.md)

<div align="center">

![CI](https://img.shields.io/github/actions/workflow/status/xiaobin1187-git/deepcover/ci.yml?branch=main)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/Java-1.8-orange)
![Maven](https://img.shields.io/badge/Maven-3.5-blue)
![Tests](https://img.shields.io/badge/Tests-52_passed-brightgreen)

</div>

> JVM Sandbox ベースの Java プレシジョン分析収集ツール - 非侵襲的なラインレベルプレシジョン分析モニタリング

## 概要

DeepCover は、Alibaba JVM Sandbox をベースにした**非侵襲型 Java プレシジョン分析収集エージェント**です。アプリケーションのソースコードを変更することなく、リアルタイムでコード行の実行状況を収集します。

### 主な特徴

- **非侵襲収集** -- JVM Sandbox バイトコード拡張技術に基づき、アプリケーションコードの変更不要
- **ラインレベルプレシジョン分析** -- コード各行の実行記録を精密に取得
- **HTTP リクエストトレーシング** -- HTTP Servlet リクエストの自動識別と追跡
- **高性能設計** -- 非同期キュー + バッチ送信により、アプリケーションへの影響を最小化
- **柔軟な設定** -- クラス名、メソッド名、サンプリングレート等のきめ細かい設定、設定センターによる動的ホットリロード対応
- **複数エクスポート方式** -- HTTP、Kafka の2つのデータエクスポート方式をサポート
- **OpenTelemetry 連携** -- OpenTelemetry 標準に準拠し、分散トレーシングに対応

### システムアーキテクチャ

コード収集からデータ処理・保存までの完全なパイプライン：

```
┌─────────────────────────────────────────────────────────────────────┐
│                      ターゲットアプリケーション JVM                    │
│                                                                     │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────────┐  │
│  │ HTTP Request │───>│  JVM Sandbox     │───>│  DeepCover Agent  │  │
│  │              │    │  (javaagent)      │    │  (収集モジュール)   │  │
│  └─────────────┘    └──────────────────┘    └────────┬──────────┘  │
│                                                        │            │
│                              ┌──────────────────────────┘            │
│                              │                                       │
│                     ┌────────┴────────┐                              │
│                     │  ローカル非同期   │  バッチ集約                   │
│                     │  キュー           │  (queue x N)                 │
│                     └────────┬────────┘                              │
│                              │                                       │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                  │
     ┌────────┴────────┐              ┌─────────┴─────────┐
     │  データセンター   │              │  Kafka Cluster     │
     │  (HTTP)          │              │  precision-analysis     │
     │  /api/collect    │              │                    │
     └────────┬────────┘              └─────────┬─────────┘
              │                                  │
              └────────────┬─────────────────────┘
                           │
              ┌────────────┴────────────┐
              │  データ処理 /            │
              │  ストレージサービス       │
              │                         │
              │  - プレシジョン分析計算         │
              │  - 差分プレシジョン分析分析     │
              │  - データ永続化          │
              │  - レポート生成          │
              └─────────────────────────┘
```

### エージェント内部アーキテクチャ

単一 JVM 内での収集フロー：

```
                        ┌─────────────────────────────────────┐
                        │          HTTP Request               │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │       HttpServlet.service()          │
                        │       (バイトコード拡張ポイント)       │
                        └──────────────┬──────────────────────┘
                                       │
 Event Flow:                           │
 ┌─────────────────────────────────────┼──────────────────────────┐
 │                                     │                          │
 │  [before]                                                      │
 │  ├─ CodeEntity を作成、ProcessTop (ThreadLocal) にバインド       │
 │  ├─ サンプリングレートチェック (traceId ハッシュベース)           │
 │  ├─ URL フィルタリング (ignoreUrls 正規表現マッチング)            │
 │  │                                                             │
 │  [beforeLine]  <── 監視対象メソッドの呼び出しごとにトリガー        │
 │  ├─ ProcessTop から CodeEntity を取得                           │
 │  ├─ NPE ガード: codeEntity==null / isSend==1 / codeInfo==null  │
 │  ├─ LineEntity を構築 (className, methodName, 行番号)           │
 │  │   └─ 行番号自動重複除去 (LinkedHashSet, O(1))                │
 │  ├─ 行番号実行回数閾値チェック (limitCodeMethodLineSize)          │
 │  │   └─ 閾値超過: REFUSE をマーク、該当メソッドの行収集を停止     │
 │  │                                                             │
 │  [after]                                                       │
 │  ├─ 収集ノード数閾値チェック (limitCodeMethodSize)               │
 │  ├─ isSend=1 をマーク、重複送信を防止                           │
 │  ├─ LocalAsyncQueue にエンキュー                                │
 │  └─ finally: ThreadLocal をクリーンアップ、メモリリーク防止       │
 │                                                                │
 └────────────────────────────────────────────────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │        LocalAsyncEngine              │
                        │                                     │
                        │  ┌─────────┐  ┌─────────┐           │
                        │  │ Queue 0 │  │ Queue 1 │  ...       │
                        │  │(バッチ)  │  │         │           │
                        │  └────┬────┘  └────┬────┘           │
                        │       │             │                │
                        │  ┌────┴─────────────┴────┐           │
                        │  │   Consumer Thread      │           │
                        │  │   HTTP / Kafka 送信     │           │
                        │  └───────────────────────┘           │
                        │                                     │
                        │  サーキットブレーカー:               │
                        │  exceptionOverflow()                 │
                        │  └─ 時間窓内で閾値超過 -> 収集停止    │
                        └─────────────────────────────────────┘
```

### 設定ホットリロード機構

ランタイム設定は再起動なしで動的に更新可能：

```
┌────────────────┐     定期ポーリング (reportPeriod) ┌──────────────────┐
│  設定センター    │ ─────────────────────────────> │  DeepCover Agent │
│  (deepcover-   │                                 │                  │
│   brain)       │ <────────────────────────────── │  reportServerInfo│
└────────────────┘     最新設定 + バージョンを返す   │                  │
                                                │  バージョン比較:    │
┌────────────────┐     syncConfig コマンド         │  info.version >   │
│  Sandbox HTTP  │ ─────────────────────────────> │  configVersion   │
│  /deepcover/   │                                 │  -> ホットリロード │
│  syncCfg       │                                 │  18 項目          │
└────────────────┘                                 └──────────────────┘
```

## 前提条件

- Java 8+ (推奨: Java 1.8.0_202+)
- Maven 3.5+
- Alibaba JVM Sandbox 1.4.0
- JVM 上で動作するアプリケーション

## クイックスタート

### 1. プロジェクトのビルド

```bash
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

ビルド成功後、`target/` ディレクトリに `deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar` が生成されます（全依存関係を含む完全 JAR）。

### 2. アプリケーション起動パラメータの設定

ターゲットアプリケーションの JVM パラメータに以下を追加：

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=your-app-name \
  -Denv=test
```

**注意**: SkyWalking APM を使用する場合、DeepCover エージェントパラメータを SkyWalking エージェントパラメータの**前に**配置してください。

### 3. DeepCover モジュールのデプロイ

```bash
# 方法1: Sandbox モジュールディレクトリにコピー
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/

# 方法2: Sandbox HTTP サービスで動的にロード
curl -X POST \
  "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"
```

### 4. インストールの確認

```bash
# ログに「ロード完了」が表示されるか確認
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

### 5. テストの実行

```bash
mvn clean test -Dmaven.javadoc.skip=true
```

現在41のユニットテストがコアユーティリティとエンティティクラスをカバーしています。

## 設定

### 基本設定

設定テンプレートからコピー：

```bash
cp src/main/resources/deepcover.properties.example src/main/resources/deepcover.properties
```

### 設定項目一覧

| 設定項目 | 説明 | デフォルト/例 |
|----------|------|---------------|
| `serviceName` | アプリケーション名 | - |
| `env` | 環境: test/pre/pro | - |
| `packageName` | 収集パッケージ名パターン（正規表現） | `com.myapp.*` |
| `ignoreClasses` | 除外クラスパターン（セミコロン区切り） | `*.logger;*.frame;` |
| `ignoreUrls` | 除外 URL（セミコロン区切り） | `/health;/metrics` |
| `ignoreAnnos` | 除外アノテーション | - |
| `sampleRate` | サンプリングレート（10000 = 100%） | `10000` |
| `limitCodeMethodSize` | リクエストあたりの最大収集メソッド数 | `500` |
| `limitCodeMethodLineSize` | メソッドあたりの最大行番号収集回数 | `500` |
| `sendDataCenterType` | 送信方式: 1=HTTP, 2=Kafka | `1` |
| `dataCenterAddr` | データセンターのアドレス（HTTP モード） | - |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka クラスタのアドレス | - |
| `KAFKA_TOPIC` | Kafka トピック | - |
| `exceptionThreshold` | サーキットブレーカーの例外閾値 | `10` |
| `exceptionCalcTime` | 例外計算時間窓（分） | `1` |
| `exceptionPauseTime` | サーキットブレーカー停止時間（秒） | `5` |

全設定項目は `syncConfig` コマンドによる動的ホットリロードに対応。アプリケーションの再起動は不要です。

### サンプリングレート

```
sampleRate=10000   # 100% サンプリング
sampleRate=5000    # 50% サンプリング
sampleRate=100     # 1% サンプリング
```

サンプリングレートは traceId のハッシュ値に基づいて計算され、異なるインスタンス間で一貫した結果が得られます。

## 動的制御 API

JVM Sandbox の HTTP サービスを通じてモジュールを動的に制御：

```bash
# モジュールの有効化
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# モジュールの無効化
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# モジュールのアンロード（完全アンロード、再収集には再起動が必要）
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=your-app-name"

# 設定の同期
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20"
```

## パフォーマンス設計

DeepCover は以下の戦略でアプリケーションへのパフォーマンス影響を最小化：

- **非同期キュー**: データ送信に独立スレッドプール（core=4, max=8, queue=256）を使用、CallerRunsPolicy でタスク消失を防止
- **バッチ処理**: ローカルキューでバッチ集約して送信、ネットワークオーバーヘッドを削減
- **サンプリング機構**: traceId ベースの確定的サンプリング、サンプリングレートをオンデマンドで調整可能
- **スマートスロットリング**: メソッドの行番号が閾値に達したら自動的に該当メソッドの行収集を停止
- **サーキットブレーカー**: 送信例外が閾値を超えたら自動的に収集を停止し、カスケード障害を防止
- **正規表現キャッシュ**: Pattern コンパイル結果をキャッシュし、ホットパスでの再コンパイルを回避

## プロジェクト構造

```
deepcover/
├── src/
│   ├── main/java/io/deepcover/agent/
│   │   ├── CodeCollecter.java       # モジュール入口、ライフサイクル管理
│   │   ├── HttpCodeModule.java      # 収集コアロジック
│   │   ├── entity/                  # データエンティティ
│   │   │   ├── CodeEntity.java      # リクエスト単位の収集データ
│   │   │   └── LineEntity.java      # メソッド単位の行情報
│   │   ├── config/
│   │   │   ├── DeepCoverConfig.java # グローバル設定
│   │   │   ├── ExecutorThreadPoolConfig.java  # スレッドプール設定
│   │   │   ├── queue/               # 非同期キューエンジン
│   │   │   └── kafka/               # Kafka 送信エンジン
│   │   ├── ext/                     # JVM Sandbox 拡張
│   │   │   ├── CodeAdviceListener.java
│   │   │   ├── CodeAdviceAdapterListener.java
│   │   │   └── CodeEventWatchBuilder.java
│   │   └── util/                    # ユーティリティクラス
│   │       ├── TraceContext.java     # TraceId 管理
│   │       ├── TraceUtil.java       # サンプリング計算
│   │       ├── ExceptionAwareUtil.java  # サーキットブレーカー
│   │       └── http/HttpClient2.java # HTTP クライアント
│   └── test/java/                   # ユニットテスト（41 ケース）
├── src/main/resources/
│   ├── deepcover.properties.example  # 設定テンプレート
│   └── logback.xml
├── sandbox/                          # JVM Sandbox バイナリと設定
├── LICENSE                           # Apache 2.0
├── CONTRIBUTING.md                   # コントリビューションガイド
├── CHANGELOG.md                      # 変更履歴
└── README.md
```

## セキュリティ

- `deepcover.properties` は `.gitignore` に含まれており、機密情報の誤ったコミットを防止
- 設定テンプレートは実際のアドレスとキーの代わりに `YOUR_*` プレースホルダーを使用
- 全依存関係はオープンソースコンポーネント、内部プライベート依存関係なし

### 依存関係

| 依存関係 | バージョン | 用途 |
|----------|-----------|------|
| Alibaba JVM Sandbox | 1.4.0 | バイトコード拡張フレームワーク |
| OpenTelemetry API | 1.30.0 | 分散トレーシング標準 |
| Apache HttpClient | 4.5.6 | HTTP データ送信 |
| Hutool | 5.8.9 | HTTP ユーティリティ（設定センター報告） |
| FastJSON | 2.0.25 | JSON シリアライズ |
| Logback | 1.2.1 | ログフレームワーク |
| Lombok | 1.18.12 | コード簡略化 |
| Kafka Clients | 2.4.1 | Kafka データ送信 |
| Guava | 18.0 | ユーティリティクラス |

## 注意事項

1. **パフォーマンス影響**: 収集にはパフォーマンスオーバーヘッドがあります。本番環境では適切なサンプリングレートを設定してください
2. **SkyWalking 互換性**: DeepCover エージェントは SkyWalking エージェントより前にロードする必要があります
3. **リソース使用量**: 収集データはメモリを消費します。`limitCodeMethodSize` をアプリケーションに合わせて調整してください
4. **テスト環境での先行検証**: 本番デプロイ前にテスト環境で十分に検証してください

## ドキュメント

- [CONTRIBUTING.md](CONTRIBUTING.md) - コントリビューションガイド
- [CHANGELOG.md](CHANGELOG.md) - 変更履歴
- [LICENSE](LICENSE) - Apache 2.0 ライセンス

## ライセンス

このプロジェクトは [Apache License 2.0](LICENSE) の下でライセンスされています。

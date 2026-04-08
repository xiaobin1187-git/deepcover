# DeepCover - Guia de Configuracao

[中文](configuration-guide.md) | [English](configuration-guide_en.md) | [日本語](configuration-guide_ja.md) | [Francais](configuration-guide_fr.md) | **Portugues** | [Русский](configuration-guide_ru.md)

## Metodos de Configuracao

O DeepCover suporta tres metodos de configuracao:

1. **Arquivo de configuracao** - `deepcover.properties` (estatico, carregado na inicializacao)
2. **Centro de configuracao** - `deepcover-brain` (dinamico, consultado periodicamente)
3. **Comando HTTP** - `syncConfig` (sob demanda, via API HTTP do Sandbox)

## Arquivo de Configuracao

### Localizacao

```
src/main/resources/deepcover.properties
```

### Formato

O arquivo e organizado por ambiente:

```properties
# Ambiente de teste
test.dataCenterAddr=http://127.0.0.1:8080/api/collect
test.configCenterAddr=http://127.0.0.1:8080
test.KAFKA_BOOTSTRAP_SERVERS=localhost:9092
test.KAFKA_TOPIC=deepcover-collection-code-info

# Ambiente de pre-producao
pre.dataCenterAddr=http://pre-dc.example.com:8080/api/collect
pre.configCenterAddr=http://pre-dc.example.com:8080
pre.KAFKA_BOOTSTRAP_SERVERS=pre-kafka.example.com:9092
pre.KAFKA_TOPIC=deepcover-collection-code-info

# Ambiente de producao
pro.dataCenterAddr=http://dc.example.com:8080/api/collect
pro.configCenterAddr=http://dc.example.com:8080
pro.KAFKA_BOOTSTRAP_SERVERS=kafka.example.com:9092
pro.KAFKA_TOPIC=deepcover-collection-code-info

# Configuracoes comuns
getDeepCoverInfo=/deepcover-brain/deepcover/info
reportServerInfo=/deepcover-brain/deepcover/report
```

O ambiente ativo e determinado pelo parametro JVM `-Ddeepcover.env=test` ou `-Denv=test`.

## Referencia de Parametros

### Parametros Principais

| Parametro | Tipo | Padrao | Descricao |
|-----------|------|--------|-----------|
| `serviceName` | String | - | Nome da aplicacao (definido via `-Dapp.name`) |
| `env` | String | - | Ambiente: test/pre/pro (definido via `-Denv` ou `-Ddeepcover.env`) |
| `branch` | String | `master` | Nome da branch Git (definido via `-Dbranch`) |

### Escopo de Coleta

| Parametro | Tipo | Padrao | Descricao |
|-----------|------|--------|-----------|
| `packageName` | Regex | - | Padrao de pacote para instrumentar. Apenas classes correspondentes a esta regex serao monitoradas. |
| `ignoreClasses` | Regex(s) | - | Padroes de classes para excluir (separados por ponto-e-virgula). Suporta regex. |
| `ignoreMethods` | Regex(s) | - | Padroes de metodos para excluir (separados por ponto-e-virgula). |
| `ignoreUrls` | String(s) | - | Caminhos URL para excluir (separados por ponto-e-virgula). Ex: `/health;/metrics` |
| `ignoreAnnos` | String(s) | - | Anotacoes para excluir. |

### Amostragem

| Parametro | Tipo | Padrao | Descricao |
|-----------|------|--------|-----------|
| `sampleRate` | Integer | `10000` | Taxa de amostragem. `10000` = 100%, `5000` = 50%, `100` = 1%. Baseada no hash do traceId para amostragem deterministica. |

### Limites de Performance

| Parametro | Tipo | Padrao | Descricao |
|-----------|------|--------|-----------|
| `limitCodeMethodSize` | Integer | `500` | Numero maximo de nos de metodo coletados por requisicao HTTP. Exceder descarta os dados da requisicao. |
| `limitCodeMethodLineSize` | Integer | `500` | Contagem maxima de coleta de linhas por metodo. Exceder para a coleta para aquele metodo. |

### Exportacao de Dados

| Parametro | Tipo | Padrao | Descricao |
|-----------|------|--------|-----------|
| `sendDataCenterType` | Integer | `1` | Metodo de exportacao: `1` = HTTP, `2` = Kafka |
| `dataCenterAddr` | URL | - | Endpoint HTTP do centro de dados para dados de cobertura |
| `KAFKA_BOOTSTRAP_SERVERS` | String | - | Servidores bootstrap do cluster Kafka |
| `KAFKA_TOPIC` | String | - | Topico Kafka para dados de cobertura |

### Configuracao de Fila

| Parametro | Tipo | Padrao | Descricao |
|-----------|------|--------|-----------|
| `queueNum` | Integer | `1` | Numero de filas assincronas internas |
| `queueSize` | Integer | `100` | Maximo de elementos por fila (capacidade ArrayBlockingQueue) |
| `queueMsgSize` | Integer | `50` | Tamanho maximo de lote para operacoes de dreno |
| `queueRecycleTime` | Integer | `10` | Tempo de sono da thread consumidora entre lotes (segundos) |

### Disjuntor

| Parametro | Tipo | Padrao | Descricao |
|-----------|------|--------|-----------|
| `exceptionThreshold` | Integer | `10` | Numero de excecoes para acionar o disjuntor |
| `exceptionCalcTime` | Integer | `1` | Janela de tempo para contagem de excecoes (minutos) |
| `exceptionPauseTime` | Integer | `5` | Duracao da pausa apos acionamento do disjuntor (segundos) |

### Relatorio

| Parametro | Tipo | Padrao | Descricao |
|-----------|------|--------|-----------|
| `reportPeriod` | Integer | `10` | Intervalo para relatorio de informacoes do servidor e consulta de config (segundos) |

## Configuracao Dinamica

### Via Comando syncConfig

Todos os parametros podem ser atualizados em tempo de execucao sem reinicializacao:

```bash
curl -X POST "http://sandbox-host:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20&limitCodeMethodSize=300"
```

### Via Centro de Configuracao

O agente consulta periodicamente o centro de configuracao (intervalo `reportPeriod`). Se a versao da config aumentar, todos os parametros sao recarregados automaticamente.

## Monitoramento

Use o endpoint de metricas para verificar o status do agente:

```bash
curl "http://sandbox-host:port/sandbox/default/module/http/deepcover/metrics"
```

Resposta incluindo:

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

## Configuracoes de Producao Recomendadas

```properties
sampleRate=1000              # 10% amostragem
limitCodeMethodSize=300      # Limite de metodos moderado
limitCodeMethodLineSize=300  # Limite de linhas moderado
exceptionThreshold=5         # Disjuntor conservador
exceptionCalcTime=1          # Janela de 1 minuto
exceptionPauseTime=10        # Pausa de 10 segundos
queueNum=2                   # Multiplas filas
queueSize=200                # Maior capacidade de fila
reportPeriod=30              # Relatorio menos frequente
```

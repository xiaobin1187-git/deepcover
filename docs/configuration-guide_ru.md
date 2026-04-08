# DeepCover - Руководство по настройке

[中文](configuration-guide.md) | [English](configuration-guide_en.md) | [日本語](configuration-guide_ja.md) | [Francais](configuration-guide_fr.md) | [Portugues](configuration-guide_pt.md) | **Русский**

## Методы настройки

DeepCover поддерживает три метода настройки:

1. **Файл конфигурации** - `deepcover.properties` (статический, загружается при запуске)
2. **Центр конфигурации** - `deepcover-brain` (динамический, опрашивается периодически)
3. **HTTP-команда** - `syncConfig` (по запросу, через Sandbox HTTP API)

## Файл конфигурации

### Расположение

```
src/main/resources/deepcover.properties
```

### Формат

Файл организован по окружениям:

```properties
# Тестовое окружение
test.dataCenterAddr=http://127.0.0.1:8080/api/collect
test.configCenterAddr=http://127.0.0.1:8080
test.KAFKA_BOOTSTRAP_SERVERS=localhost:9092
test.KAFKA_TOPIC=deepcover-collection-code-info

# Предпродуктивное окружение
pre.dataCenterAddr=http://pre-dc.example.com:8080/api/collect
pre.configCenterAddr=http://pre-dc.example.com:8080
pre.KAFKA_BOOTSTRAP_SERVERS=pre-kafka.example.com:9092
pre.KAFKA_TOPIC=deepcover-collection-code-info

# Продуктивное окружение
pro.dataCenterAddr=http://dc.example.com:8080/api/collect
pro.configCenterAddr=http://dc.example.com:8080
pro.KAFKA_BOOTSTRAP_SERVERS=kafka.example.com:9092
pro.KAFKA_TOPIC=deepcover-collection-code-info

# Общие настройки
getDeepCoverInfo=/deepcover-brain/deepcover/info
reportServerInfo=/deepcover-brain/deepcover/report
```

Активное окружение определяется параметром JVM `-Ddeepcover.env=test` или `-Denv=test`.

## Справочник параметров

### Основные параметры

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `serviceName` | String | - | Имя приложения (задается через `-Dapp.name`) |
| `env` | String | - | Окружение: test/pre/pro (задается через `-Denv` или `-Ddeepcover.env`) |
| `branch` | String | `master` | Имя ветки Git (задается через `-Dbranch`) |

### Область сбора

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `packageName` | Regex | - | Шаблон пакетов для инструментирования. Только классы, соответствующие этому regex, будут отслеживаться. |
| `ignoreClasses` | Regex(s) | - | Шаблоны классов для исключения (через точку с запятой). Поддерживает regex. |
| `ignoreMethods` | Regex(s) | - | Шаблоны методов для исключения (через точку с запятой). |
| `ignoreUrls` | String(s) | - | URL-пути для исключения (через точку с запятой). Пример: `/health;/metrics` |
| `ignoreAnnos` | String(s) | - | Аннотации для исключения. |

### Выборка

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `sampleRate` | Integer | `10000` | Частота выборки. `10000` = 100%, `5000` = 50%, `100` = 1%. На основе хеша traceId для детерминированной выборки. |

### Ограничения производительности

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `limitCodeMethodSize` | Integer | `500` | Максимальное количество узлов методов, собираемых за один HTTP-запрос. При превышении данные запроса отбрасываются. |
| `limitCodeMethodLineSize` | Integer | `500` | Максимальное количество сборов строк на метод. При превышении сбор строк для этого метода прекращается. |

### Экспорт данных

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `sendDataCenterType` | Integer | `1` | Метод экспорта: `1` = HTTP, `2` = Kafka |
| `dataCenterAddr` | URL | - | HTTP-эндпоинт центра данных для данных покрытия |
| `KAFKA_BOOTSTRAP_SERVERS` | String | - | Bootstrap-серверы кластера Kafka |
| `KAFKA_TOPIC` | String | - | Топик Kafka для данных покрытия |

### Конфигурация очереди

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `queueNum` | Integer | `1` | Количество внутренних асинхронных очередей |
| `queueSize` | Integer | `100` | Максимальное количество элементов в очереди (емкость ArrayBlockingQueue) |
| `queueMsgSize` | Integer | `50` | Максимальный размер пакета для операций слива очереди |
| `queueRecycleTime` | Integer | `10` | Время сна потока-потребителя между пакетами (секунды) |

### Автомат защиты

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `exceptionThreshold` | Integer | `10` | Количество исключений для срабатывания автомата защиты |
| `exceptionCalcTime` | Integer | `1` | Временное окно подсчета исключений (минуты) |
| `exceptionPauseTime` | Integer | `5` | Длительность паузы после срабатывания автомата (секунды) |

### Отчетность

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `reportPeriod` | Integer | `10` | Интервал отчетов о сервере и опроса конфигурации (секунды) |

## Динамическая настройка

### Через команду syncConfig

Все параметры можно обновлять во время выполнения без перезапуска:

```bash
curl -X POST "http://sandbox-host:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20&limitCodeMethodSize=300"
```

### Через центр конфигурации

Агент периодически опрашивает центр конфигурации (интервал `reportPeriod`). При увеличении версии конфигурации все параметры автоматически перезагружаются.

## Мониторинг

Используйте эндпоинт метрик для проверки состояния агента:

```bash
curl "http://sandbox-host:port/sandbox/default/module/http/deepcover/metrics"
```

Ответ включает:

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

## Рекомендуемые продуктивные настройки

```properties
sampleRate=1000              # 10% выборка
limitCodeMethodSize=300      # Умеренное ограничение методов
limitCodeMethodLineSize=300  # Умеренное ограничение строк
exceptionThreshold=5         # Консервативный автомат защиты
exceptionCalcTime=1          # Окно 1 минута
exceptionPauseTime=10        # Пауза 10 секунд
queueNum=2                   # Несколько очередей
queueSize=200                # Большая емкость очереди
reportPeriod=30              # Менее частая отчетность
```

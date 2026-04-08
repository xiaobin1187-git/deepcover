# DeepCover - Руководство по развертыванию

[中文](deployment-guide.md) | [English](deployment-guide_en.md) | [日本語](deployment-guide_ja.md) | [Francais](deployment-guide_fr.md) | [Portugues](deployment-guide_pt.md) | **Русский**

## Требования к окружению

| Компонент | Версия | Примечания |
|-----------|--------|------------|
| Java | 8+ | Рекомендуется: 1.8.0_202+ |
| Maven | 3.5+ | Для сборки агента |
| JVM Sandbox | 1.4.0 | Фреймворк инструментирования байт-кода |
| Servlet-контейнер | 3.0+ | Tomcat, Jetty и т.д. |

## Шаг 1: Сборка агента DeepCover

```bash
git clone https://github.com/xiaobin1187-git/deepcover.git
cd deepcover
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

Результат: `target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Шаг 2: Установка JVM Sandbox

Скачайте JVM Sandbox из [официального репозитория](https://github.com/alibaba/jvm-sandbox) и распакуйте:

```
sandbox/
├── bin/
│   └── sandbox.sh
├── cfg/
│   └── sandbox.properties
├── lib/
│   ├── sandbox-agent.jar    # javaagent jar
│   └── sandbox-spy.jar
└── sandbox-module/          # Здесь размещается модуль DeepCover
```

## Шаг 3: Развертывание модуля DeepCover

```bash
# Скопировать собранный агент в каталог модулей Sandbox
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/
```

## Шаг 4: Настройка целевого приложения

Добавьте параметры JVM в скрипт запуска приложения:

### Tomcat

Отредактируйте `bin/setenv.sh` (или `catalina.sh`):

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=имя-приложения \
  -Denv=test \
  -Ddeepcover.env=test \
  -Dbranch=master"
```

### Spring Boot (java -jar)

```bash
java -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
     -Dapp.name=имя-приложения \
     -Denv=test \
     -Ddeepcover.env=test \
     -jar ваше-приложение.jar
```

### Важно: Совместимость со SkyWalking

При использовании SkyWalking APM параметры агента DeepCover должны располагаться **перед** параметрами SkyWalking:

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \     # Сначала DeepCover
-javaagent:/path/to/skywalking-agent.jar \               # Затем SkyWalking
```

## Шаг 5: Настройка deepcover.properties

Скопируйте и отредактируйте шаблон конфигурации:

```bash
cp src/main/resources/deepcover.properties.example \
   src/main/resources/deepcover.properties
```

Подробное описание параметров см. в [руководстве по настройке](configuration-guide_ru.md).

## Шаг 6: Проверка установки

### Проверка загрузки модуля

```bash
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

Должно появиться сообщение об успешной загрузке модуля.

### Проверка эндпоинта метрик

```bash
curl "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/metrics"
```

Ожидается JSON-ответ со статистикой времени выполнения.

## Шаг 7: Управление жизненным циклом модуля

```bash
# Активировать модуль
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# Деактивировать модуль (можно реактивировать)
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# Полностью выгрузить модуль (для повторного включения нужен перезапуск)
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=имя-приложения"
```

## Устранение неполадок

### Модуль не загружается

- Проверьте правильность пути к `sandbox-agent.jar`
- Убедитесь, что заданы параметры JVM `app.name` и `env`
- Проверьте `~/sandbox/sandbox.log` для подробностей ошибки

### Данные покрытия не собираются

- Убедитесь, что regex `packageName` соответствует пакетам приложения
- Проверьте правильность секции `env` в `deepcover.properties`
- Убедитесь, что центр данных или Kafka доступен с хоста приложения

### Слишком высокое влияние на производительность

- Уменьшите `sampleRate` (например, `5000` для 50%)
- Снизьте `limitCodeMethodSize` и `limitCodeMethodLineSize`
- Проверьте работу автомата через эндпоинт `/metrics`

### Предупреждения о переполнении очереди

- Увеличьте `queueSize` (по умолчанию: 100)
- Увеличьте `queueNum` (по умолчанию: 1)
- Уменьшите `sampleRate` для снижения объема данных

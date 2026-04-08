# DeepCover Демо - Servlet Приложение

[中文](README.md) | [English](README_EN.md) | [日本語](README_ja.md) | [Francais](README_fr.md) | [Portugues](README_pt.md) | **Русский**

Простое веб-приложение, демонстрирующее, как DeepCover собирает данные о покрытии кода.

## Предварительные требования

- Java 8+
- Maven 3.5+
- Tomcat 8+ (или любой Servlet-контейнер)
- [DeepCover Agent](../../) собран и готов к использованию

## Быстрый старт

### 1. Сборка демо-проекта

```bash
cd examples/demo-servlet
mvn clean package
```

Будет сгенерирован файл `target/demo-servlet.war`.

### 2. Настройка JVM с агентом DeepCover

Добавьте в `catalina.sh` Tomcat (или `setenv.sh`):

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=demo-servlet \
  -Denv=test \
  -Ddeepcover.env=test"
```

### 3. Развертывание и тестирование

```bash
# Копировать WAR в Tomcat
cp target/demo-servlet.war /path/to/tomcat/webapps/

# Запустить Tomcat
/path/to/tomcat/bin/startup.sh

# Тестирование эндпоинтов
curl "http://localhost:8080/demo-servlet/user?action=list"
curl "http://localhost:8080/demo-servlet/user?action=get&id=1"
curl "http://localhost:8080/demo-servlet/user?action=create&name=Bob&email=bob@example.com"
curl "http://localhost:8080/demo-servlet/user?action=delete&id=1"
```

### 4. Проверка метрик

```bash
# Просмотр метрик времени выполнения DeepCover
curl "http://localhost:port/sandbox/default/module/http/deepcover/metrics"
```

## Что собирает DeepCover

Когда HTTP-запросы достигают `UserServlet.service()`, DeepCover выполняет:

1. **Трассировка запроса** - Запись HTTP-метода, URL и traceId
2. **Инструментализация вызовов методов** - Отслеживание всех вызовов методов в пакете `io.deepcover.examples.demo.*`
3. **Сбор номеров строк** - Запись того, какие строки кода были выполнены в `UserService`
4. **Отправка данных** - Передача данных о покрытии в настроенный центр обработки данных или топик Kafka

## Пример данных покрытия

Для запроса `GET /user?action=get&id=1` DeepCover собирает:

- Класс: `io.deepcover.examples.demo.controller.UserServlet`
- Метод: `service` - выполненные строки: 24, 25, 26, 29, 34
- Класс: `io.deepcover.examples.demo.service.UserService`
- Метод: `getUser` - выполненные строки: 22, 23, 26, 27

Эти данные можно использовать для расчета покрытия кода на уровне строк для каждого HTTP-запроса.

## Конфигурация

Убедитесь, что `deepcover.properties` содержит правильные настройки:

```properties
# Шаблон пакета для сбора
test.packageName=io\\.deepcover\\.examples\\.demo\\..*
```

Или настройте через центр конфигурации с помощью `packageName=io\.deepcover\.examples\.demo\..*`.

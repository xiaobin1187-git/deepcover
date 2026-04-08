# DeepCover Demo - Aplicacao Servlet

[中文](README.md) | [English](README_EN.md) | [日本語](README_ja.md) | [Francais](README_fr.md) | **Portugues** | [Русский](README_ru.md)

Uma aplicacao web simples demonstrando como o DeepCover coleta dados de cobertura de codigo.

## Pre-requisitos

- Java 8+
- Maven 3.5+
- Tomcat 8+ (ou qualquer conteiner Servlet)
- [DeepCover Agent](../../) compilado e pronto para uso

## Inicio rapido

### 1. Compilar o demo

```bash
cd examples/demo-servlet
mvn clean package
```

Isso gera `target/demo-servlet.war`.

### 2. Configurar a JVM com o agente DeepCover

Adicione ao `catalina.sh` do Tomcat (ou `setenv.sh`):

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=demo-servlet \
  -Denv=test \
  -Ddeepcover.env=test"
```

### 3. Implantar e testar

```bash
# Copiar o WAR para o Tomcat
cp target/demo-servlet.war /path/to/tomcat/webapps/

# Iniciar o Tomcat
/path/to/tomcat/bin/startup.sh

# Testar os endpoints
curl "http://localhost:8080/demo-servlet/user?action=list"
curl "http://localhost:8080/demo-servlet/user?action=get&id=1"
curl "http://localhost:8080/demo-servlet/user?action=create&name=Bob&email=bob@example.com"
curl "http://localhost:8080/demo-servlet/user?action=delete&id=1"
```

### 4. Verificar metricas

```bash
# Visualizar metricas de tempo de execucao do DeepCover
curl "http://localhost:port/sandbox/default/module/http/deepcover/metrics"
```

## O que o DeepCover coleta

Quando as requisicoes HTTP atingem `UserServlet.service()`, o DeepCover ira:

1. **Rastrear a requisicao** - Registrar o metodo HTTP, URL e traceId
2. **Instrumentar chamadas de metodo** - Rastrear todas as chamadas de metodo dentro do pacote `io.deepcover.examples.demo.*`
3. **Coletar numeros de linha** - Registrar quais linhas de codigo foram executadas em `UserService`
4. **Enviar dados** - Transmitir os dados de cobertura para o centro de dados configurado ou topico Kafka

## Exemplo de dados de cobertura

Para uma requisicao `GET /user?action=get&id=1`, o DeepCover coleta:

- Classe: `io.deepcover.examples.demo.controller.UserServlet`
- Metodo: `service` - linhas executadas: 24, 25, 26, 29, 34
- Classe: `io.deepcover.examples.demo.service.UserService`
- Metodo: `getUser` - linhas executadas: 22, 23, 26, 27

Esses dados podem ser usados para calcular a cobertura de codigo em nivel de linha por requisicao HTTP.

## Configuracao

Certifique-se de que `deepcover.properties` tenha as configuracoes corretas:

```properties
# Padrao de pacote para coletar
test.packageName=io\\.deepcover\\.examples\\.demo\\..*
```

Ou configure via centro de configuracao com `packageName=io\.deepcover\.examples\.demo\..*`.

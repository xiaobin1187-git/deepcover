# DeepCover - Guia de Implantacao

[中文](deployment-guide.md) | [English](deployment-guide_en.md) | [日本語](deployment-guide_ja.md) | [Francais](deployment-guide_fr.md) | **Portugues** | [Русский](deployment-guide_ru.md)

## Requisitos de Ambiente

| Componente | Versao | Observacoes |
|------------|--------|-------------|
| Java | 8+ | Recomendado: 1.8.0_202+ |
| Maven | 3.5+ | Para compilar o agente |
| JVM Sandbox | 1.4.0 | Framework de instrumentacao bytecode |
| Container Servlet | 3.0+ | Tomcat, Jetty, etc. |

## Passo 1: Compilar o Agente DeepCover

```bash
git clone https://github.com/xiaobin1187-git/deepcover.git
cd deepcover
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

Saida: `target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Passo 2: Instalar o JVM Sandbox

Baixe o JVM Sandbox do [repositorio oficial](https://github.com/alibaba/jvm-sandbox) e extraia:

```
sandbox/
├── bin/
│   └── sandbox.sh
├── cfg/
│   └── sandbox.properties
├── lib/
│   ├── sandbox-agent.jar    # javaagent jar
│   └── sandbox-spy.jar
└── sandbox-module/          # Colocar o modulo DeepCover aqui
```

## Passo 3: Implantar o Modulo DeepCover

```bash
# Copiar o agente compilado para o diretorio de modulos do Sandbox
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/
```

## Passo 4: Configurar a Aplicacao Alvo

Adicione os parametros JVM ao script de inicializacao da aplicacao:

### Tomcat

Edite `bin/setenv.sh` (ou `catalina.sh`):

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=sua-aplicacao \
  -Denv=test \
  -Ddeepcover.env=test \
  -Dbranch=master"
```

### Spring Boot (java -jar)

```bash
java -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
     -Dapp.name=sua-aplicacao \
     -Denv=test \
     -Ddeepcover.env=test \
     -jar sua-aplicacao.jar
```

### Importante: Compatibilidade com SkyWalking

Se estiver usando SkyWalking APM, os parametros do agente DeepCover devem vir **antes** dos do SkyWalking:

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \     # DeepCover primeiro
-javaagent:/path/to/skywalking-agent.jar \               # SkyWalking depois
```

## Passo 5: Configurar o deepcover.properties

Copie e edite o modelo de configuracao:

```bash
cp src/main/resources/deepcover.properties.example \
   src/main/resources/deepcover.properties
```

Consulte o [guia de configuracao](configuration-guide_pt.md) para descricao detalhada dos parametros.

## Passo 6: Verificar a Instalacao

### Verificar o Carregamento do Modulo

```bash
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

A mensagem de carregamento concluido do modulo deve aparecer.

### Verificar o Endpoint de Metricas

```bash
curl "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/metrics"
```

Resposta JSON esperada com estatisticas de tempo de execucao.

## Passo 7: Controlar o Ciclo de Vida do Modulo

```bash
# Ativar modulo
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# Desativar modulo (pode ser reativado)
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# Descarregar modulo completamente (requer reinicializacao para reativar)
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=sua-aplicacao"
```

## Solucao de Problemas

### Falha ao carregar o modulo

- Verifique se o caminho de `sandbox-agent.jar` esta correto
- Verifique se os parametros JVM `app.name` e `env` estao definidos
- Consulte `~/sandbox/sandbox.log` para detalhes do erro

### Nenhum dado de cobertura coletado

- Verifique se a regex `packageName` corresponde aos pacotes da aplicacao
- Verifique se a secao `env` em `deepcover.properties` esta correta
- Verifique se o centro de dados ou Kafka e acessivel a partir do host

### Impacto no desempenho muito alto

- Reduza `sampleRate` (ex: `5000` para 50%)
- Reduza `limitCodeMethodSize` e `limitCodeMethodLineSize`
- Verifique o funcionamento do disjuntor via endpoint `/metrics`

### Avisos de fila cheia

- Aumente `queueSize` (padrao: 100)
- Aumente `queueNum` (padrao: 1)
- Reduza `sampleRate` para diminuir o volume de dados

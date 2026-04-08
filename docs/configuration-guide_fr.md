# DeepCover - Guide de configuration

[中文](configuration-guide.md) | [English](configuration-guide_en.md) | [日本語](configuration-guide_ja.md) | **Francais** | [Portugues](configuration-guide_pt.md) | [Русский](configuration-guide_ru.md)

## Methodes de configuration

DeepCover supporte trois methodes de configuration:

1. **Fichier de configuration** - `deepcover.properties` (statique, charge au demarrage)
2. **Centre de configuration** - `deepcover-brain` (dynamique, interroge periodiquement)
3. **Commande HTTP** - `syncConfig` (a la demande, via l'API HTTP Sandbox)

## Fichier de configuration

### Emplacement

```
src/main/resources/deepcover.properties
```

### Format

Le fichier est organise par environnement:

```properties
# Environnement de test
test.dataCenterAddr=http://127.0.0.1:8080/api/collect
test.configCenterAddr=http://127.0.0.1:8080
test.KAFKA_BOOTSTRAP_SERVERS=localhost:9092
test.KAFKA_TOPIC=deepcover-collection-code-info

# Environnement de pre-production
pre.dataCenterAddr=http://pre-dc.example.com:8080/api/collect
pre.configCenterAddr=http://pre-dc.example.com:8080
pre.KAFKA_BOOTSTRAP_SERVERS=pre-kafka.example.com:9092
pre.KAFKA_TOPIC=deepcover-collection-code-info

# Environnement de production
pro.dataCenterAddr=http://dc.example.com:8080/api/collect
pro.configCenterAddr=http://dc.example.com:8080
pro.KAFKA_BOOTSTRAP_SERVERS=kafka.example.com:9092
pro.KAFKA_TOPIC=deepcover-collection-code-info

# Parametres communs
getDeepCoverInfo=/deepcover-brain/deepcover/info
reportServerInfo=/deepcover-brain/deepcover/report
```

L'environnement actif est determine par le parametre JVM `-Ddeepcover.env=test` ou `-Denv=test`.

## Reference des parametres

### Parametres principaux

| Parametre | Type | Defaut | Description |
|-----------|------|--------|-------------|
| `serviceName` | String | - | Nom de l'application (defini via `-Dapp.name`) |
| `env` | String | - | Environnement: test/pre/pro (defini via `-Denv` ou `-Ddeepcover.env`) |
| `branch` | String | `master` | Nom de la branche Git (defini via `-Dbranch`) |

### Portee de collecte

| Parametre | Type | Defaut | Description |
|-----------|------|--------|-------------|
| `packageName` | Regex | - | Pattern de package a instrumenter. Seules les classes correspondant a cette regex seront surveillees. |
| `ignoreClasses` | Regex(s) | - | Patterns de classes a exclure (separees par point-virgule). Supporte les regex. |
| `ignoreMethods` | Regex(s) | - | Patterns de methodes a exclure (separees par point-virgule). |
| `ignoreUrls` | String(s) | - | Chemins URL a exclure (separees par point-virgule). Ex: `/health;/metrics` |
| `ignoreAnnos` | String(s) | - | Annotations a exclure. |

### Echantillonnage

| Parametre | Type | Defaut | Description |
|-----------|------|--------|-------------|
| `sampleRate` | Integer | `10000` | Taux d'echantillonnage. `10000` = 100%, `5000` = 50%, `100` = 1%. Base sur le hash du traceId pour un echantillonnage deterministe. |

### Limites de performance

| Parametre | Type | Defaut | Description |
|-----------|------|--------|-------------|
| `limitCodeMethodSize` | Integer | `500` | Nombre maximum de noeuds de methode collectes par requete HTTP. Le depassement entraine l'abandon des donnees. |
| `limitCodeMethodLineSize` | Integer | `500` | Nombre maximum de collections de lignes par methode. Le depassement arrete la collecte pour cette methode. |

### Export de donnees

| Parametre | Type | Defaut | Description |
|-----------|------|--------|-------------|
| `sendDataCenterType` | Integer | `1` | Methode d'export: `1` = HTTP, `2` = Kafka |
| `dataCenterAddr` | URL | - | Endpoint HTTP du centre de donnees pour les donnees de couverture |
| `KAFKA_BOOTSTRAP_SERVERS` | String | - | Serveurs bootstrap du cluster Kafka |
| `KAFKA_TOPIC` | String | - | Topic Kafka pour les donnees de couverture |

### Configuration de la file

| Parametre | Type | Defaut | Description |
|-----------|------|--------|-------------|
| `queueNum` | Integer | `1` | Nombre de files asynchrones internes |
| `queueSize` | Integer | `100` | Maximum d'elements par file (capacite ArrayBlockingQueue) |
| `queueMsgSize` | Integer | `50` | Taille maximale des lots pour les operations de vidange |
| `queueRecycleTime` | Integer | `10` | Temps de sommeil du thread consommateur entre les lots (secondes) |

### Disjoncteur

| Parametre | Type | Defaut | Description |
|-----------|------|--------|-------------|
| `exceptionThreshold` | Integer | `10` | Nombre d'exceptions pour declencher le disjoncteur |
| `exceptionCalcTime` | Integer | `1` | Fenetre de temps pour le comptage des exceptions (minutes) |
| `exceptionPauseTime` | Integer | `5` | Duree de pause apres le declenchement du disjoncteur (secondes) |

### Rapport

| Parametre | Type | Defaut | Description |
|-----------|------|--------|-------------|
| `reportPeriod` | Integer | `10` | Intervalle de rapport d'informations serveur et d'interrogation de config (secondes) |

## Configuration dynamique

### Via la commande syncConfig

Tous les parametres peuvent etre mis a jour en cours d'execution sans redemarrage:

```bash
curl -X POST "http://sandbox-host:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20&limitCodeMethodSize=300"
```

### Via le centre de configuration

L'agent interroge periodiquement le centre de configuration (intervalle `reportPeriod`). Si la version de config augmente, tous les parametres sont automatiquement recharge.

## Surveillance

Utilisez l'endpoint de metriques pour verifier le statut de l'agent:

```bash
curl "http://sandbox-host:port/sandbox/default/module/http/deepcover/metrics"
```

Reponse incluant:

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

## Parametres de production recommandes

```properties
sampleRate=1000              # 10% echantillonnage
limitCodeMethodSize=300      # Limite de methodes moderee
limitCodeMethodLineSize=300  # Limite de lignes moderee
exceptionThreshold=5         # Disjoncteur conservateur
exceptionCalcTime=1          # Fenetre de 1 minute
exceptionPauseTime=10        # Pause de 10 secondes
queueNum=2                   # Files multiples
queueSize=200                # Capacite de file plus grande
reportPeriod=30              # Rapport moins frequent
```

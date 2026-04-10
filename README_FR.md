<div align="center"><img src="docs/assets/logo.svg" alt="DeepCover" width="96" height="96"></div>
# DeepCover - Agent de collecte de analyse de precision en chaine complete

[中文](README.md) | [English](README_EN.md) | [日本語](README_JA.md) | **Francais** | [Portugues](README_PT.md) | [Русский](README_RU.md)

<div align="center">

![CI](https://img.shields.io/github/actions/workflow/status/xiaobin1187-git/deepcover/ci.yml?branch=main)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/Java-1.8-orange)
![Maven](https://img.shields.io/badge/Maven-3.5-blue)
![Tests](https://img.shields.io/badge/Tests-52_passed-brightgreen)

</div>

> Outil de collecte de analyse de precision Java base sur JVM Sandbox - Surveillance de analyse de precision au niveau des lignes, non intrusive

## Introduction

DeepCover est un **agent de collecte de analyse de precision Java non intrusif** base sur Alibaba JVM Sandbox. Il collecte en temps reel les donnees d'execution des lignes de code sans modifier le code source de l'application.

### Fonctionnalites principales

- **Collecte non intrusive** -- Base sur la technologie d'amelioration de bytecode JVM Sandbox, aucune modification du code source requise
- **Analyse au niveau des lignes** -- Enregistrements d'execution precis pour chaque ligne de code
- **Tracage des requetes HTTP** -- Identification et suivi automatiques des requetes HTTP Servlet
- **Conception haute performance** -- Files asynchrones + envoi par lots pour minimiser l'impact sur l'application
- **Configuration flexible** -- Controle fin des noms de classes, methodes, taux d'echantillonnage, avec rechargement a chaud dynamique via le centre de configuration
- **Multiples methodes d'export** -- Supporte l'export via HTTP et Kafka
- **Integration OpenTelemetry** -- Compatible avec la norme OpenTelemetry pour le tracage distribue

### Architecture systeme

Le pipeline complet de la collecte de code au traitement et stockage des donnees :

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JVM de l'application cible                       │
│                                                                     │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────────┐  │
│  │ HTTP Request │───>│  JVM Sandbox     │───>│  DeepCover Agent  │  │
│  │              │    │  (javaagent)      │    │  (Collecte)       │  │
│  └─────────────┘    └──────────────────┘    └────────┬──────────┘  │
│                                                        │            │
│                              ┌──────────────────────────┘            │
│                              │                                       │
│                     ┌────────┴────────┐                              │
│                     │  File asynchrone │  Agregation par lots         │
│                     │  locale          │  (queue x N)                 │
│                     └────────┬────────┘                              │
│                              │                                       │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                  │
     ┌────────┴────────┐              ┌─────────┴─────────┐
     │  Centre de       │              │  Cluster Kafka     │
     │  donnees (HTTP)  │              │  precision-analysis     │
     │  /api/collect    │              │                    │
     └────────┬────────┘              └─────────┬─────────┘
              │                                  │
              └────────────┬─────────────────────┘
                           │
              ┌────────────┴────────────┐
              │  Traitement des donnees │
              │  / Service de stockage  │
              │                         │
              │  - Calcul d analyse de precision  │
              │  - Analyse diff.        │
              │  - Persistence des      │
              │    donnees              │
              │  - Generation rapports  │
              └─────────────────────────┘
```

### Architecture interne de l'agent

Le flux de collecte au sein d'une seule JVM :

```
                        ┌─────────────────────────────────────┐
                        │          Requete HTTP               │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │       HttpServlet.service()          │
                        │    (Point d'instrumentation bytecode)│
                        └──────────────┬──────────────────────┘
                                       │
 Flux d'evenements :                   │
 ┌─────────────────────────────────────┼──────────────────────────┐
 │                                     │                          │
 │  [before]                                                      │
 │  ├─ Creer CodeEntity, lier a ProcessTop (ThreadLocal)          │
 │  ├─ Verification du taux d'echantillonnage (hash traceId)      │
 │  ├─ Filtrage URL (regex ignoreUrls)                            │
 │  │                                                             │
 │  [beforeLine]  <── declenche a chaque appel de methode surveille│
 │  ├─ Recuperer CodeEntity depuis ProcessTop                     │
 │  ├─ Protection NPE: codeEntity==null / isSend==1               │
 │  ├─ Construire LineEntity (className, methodName, n° ligne)    │
 │  │   └─ Dedoublonnage auto des n° de ligne (LinkedHashSet)     │
 │  ├─ Verification du seuil de nombre d'executions de ligne      │
 │  │   └─ Seuil depasse: marquer REFUSE, arreter la collecte     │
 │  │                                                             │
 │  [after]                                                       │
 │  ├─ Verification du seuil de nœuds collectes                   │
 │  ├─ Marquer isSend=1, empecher les envois en double            │
 │  ├─ Mettre dans la file LocalAsyncQueue                       │
 │  └─ finally: nettoyer ThreadLocal, prevenir les fuites memoire │
 │                                                                │
 └────────────────────────────────────────────────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │        LocalAsyncEngine              │
                        │                                     │
                        │  ┌─────────┐  ┌─────────┐           │
                        │  │ Queue 0 │  │ Queue 1 │  ...       │
                        │  │ (lots)  │  │         │           │
                        │  └────┬────┘  └────┬────┘           │
                        │       │             │                │
                        │  ┌────┴─────────────┴────┐           │
                        │  │   Thread Consommateur  │           │
                        │  │   Envoi HTTP / Kafka   │           │
                        │  └───────────────────────┘           │
                        │                                     │
                        │  Disjoncteur: exceptionOverflow()    │
                        │  └─ Fenetre de temps depassee ->     │
                        │     pause de la collecte              │
                        └─────────────────────────────────────┘
```

### Mecanisme de rechargement a chaud de la configuration

La configuration runtime peut etre mise a jour dynamiquement sans redemarrage :

```
┌────────────────┐   Sondage periodique (reportPeriod) ┌──────────────────┐
│  Centre de      │ ─────────────────────────────────> │  DeepCover Agent │
│  configuration  │                                    │                  │
│  (deepcover-    │ <───────────────────────────────── │  reportServerInfo│
│   brain)        │   Retourne config + version        │                  │
└────────────────┘                                    │  Comparaison:    │
                                                      │  info.version >  │
┌────────────────┐   Commande syncConfig              │  configVersion   │
│  HTTP Sandbox  │ ─────────────────────────────────> │  -> rechargement │
│  /deepcover/   │                                    │     a chaud      │
│  syncCfg       │                                    │  18 parametres   │
└────────────────┘                                    └──────────────────┘
```

## Prerequis

- Java 8+ (recommande: Java 1.8.0_202+)
- Maven 3.5+
- Alibaba JVM Sandbox 1.4.0
- Application s'executant sur JVM

## Demarrage rapide

### 1. Compiler le projet

```bash
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

Apres compilation reussie, le fichier `deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar` est genere dans le repertoire `target/`.

### 2. Configurer les parametres JVM

Ajoutez les parametres suivants aux arguments JVM de l'application cible :

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=votre-app \
  -Denv=test
```

**Attention** : Si vous utilisez SkyWalking APM, placez les parametres de l'agent DeepCover **avant** ceux de SkyWalking.

### 3. Deployer le module DeepCover

```bash
# Methode 1: Copier dans le repertoire des modules Sandbox
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/

# Methode 2: Chargement dynamique via le service HTTP Sandbox
curl -X POST \
  "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"
```

### 4. Verifier l'installation

```bash
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

### 5. Executer les tests

```bash
mvn clean test -Dmaven.javadoc.skip=true
```

41 tests unitaires couvrant les classes utilitaires et entites principales.

## Configuration

### Configuration de base

Copiez le modele de configuration :

```bash
cp src/main/resources/deepcover.properties.example src/main/resources/deepcover.properties
```

### Reference des parametres

| Parametre | Description | Defaut/Exemple |
|-----------|-------------|----------------|
| `serviceName` | Nom de l'application | - |
| `env` | Environnement : test/pre/pro | - |
| `packageName` | Pattern de paquets (regex) | `com.myapp.*` |
| `ignoreClasses` | Classes ignorees (point-virgule) | `*.logger;*.frame;` |
| `ignoreUrls` | URLs ignorees (point-virgule) | `/health;/metrics` |
| `ignoreAnnos` | Annotations ignorees | - |
| `sampleRate` | Taux d'echantillonnage (10000 = 100%) | `10000` |
| `limitCodeMethodSize` | Max methodes collectees par requete | `500` |
| `limitCodeMethodLineSize` | Max collections de lignes par methode | `500` |
| `sendDataCenterType` | Methode d'envoi : 1=HTTP, 2=Kafka | `1` |
| `dataCenterAddr` | Adresse du centre de donnees (HTTP) | - |
| `KAFKA_BOOTSTRAP_SERVERS` | Adresse du cluster Kafka | - |
| `KAFKA_TOPIC` | Topic Kafka | - |
| `exceptionThreshold` | Seuil du disjoncteur | `10` |
| `exceptionCalcTime` | Fenetre de calcul des exceptions (min) | `1` |
| `exceptionPauseTime` | Temps de pause du disjoncteur (sec) | `5` |

Tous les parametres supportent le rechargement a chaud via la commande `syncConfig`.

### Taux d'echantillonnage

```
sampleRate=10000   # 100% echantillonnage
sampleRate=5000    # 50% echantillonnage
sampleRate=100     # 1% echantillonnage
```

Le taux est calcule sur le hash du traceId, assurant des resultats coherents entre les instances.

## API de controle dynamique

Controlez dynamiquement le module via le service HTTP de JVM Sandbox :

```bash
# Activer le module
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# Desactiver le module
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# Decharger le module
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=your-app-name"

# Synchroniser la configuration
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20"
```

## Conception de performance

DeepCover minimise l'impact sur les performances de l'application via :

- **Files asynchrones** : Pool de threads independant (core=4, max=8, queue=256) avec CallerRunsPolicy
- **Traitement par lots** : Agregation locale pour envoi par lots, reduction des couts reseau
- **Echantillonnage** : Echantillonnage deterministe base sur le traceId
- **Limitation intelligente** : Arret automatique de la collecte des lignes quand le seuil est atteint
- **Disjoncteur** : Pause automatique quand les exceptions depassent le seuil
- **Cache regex** : Mise en cache des resultats de compilation Pattern

## Structure du projet

```
deepcover/
├── src/
│   ├── main/java/io/deepcover/agent/
│   │   ├── CodeCollecter.java       # Entree du module, gestion du cycle de vie
│   │   ├── HttpCodeModule.java      # Logique de collecte principale
│   │   ├── entity/                  # Entites de donnees
│   │   │   ├── CodeEntity.java      # Donnees de collecte par requete
│   │   │   └── LineEntity.java      # Infos de ligne par methode
│   │   ├── config/
│   │   │   ├── DeepCoverConfig.java # Configuration globale
│   │   │   ├── ExecutorThreadPoolConfig.java  # Config pool de threads
│   │   │   ├── queue/               # Moteur de file asynchrone
│   │   │   └── kafka/               # Moteur d'envoi Kafka
│   │   ├── ext/                     # Extensions JVM Sandbox
│   │   │   ├── CodeAdviceListener.java
│   │   │   ├── CodeAdviceAdapterListener.java
│   │   │   └── CodeEventWatchBuilder.java
│   │   └── util/                    # Classes utilitaires
│   │       ├── TraceContext.java     # Gestion TraceId
│   │       ├── TraceUtil.java       # Calcul d'echantillonnage
│   │       ├── ExceptionAwareUtil.java  # Disjoncteur
│   │       └── http/HttpClient2.java # Client HTTP
│   └── test/java/                   # Tests unitaires (41 cas)
├── src/main/resources/
│   ├── deepcover.properties.example  # Modele de config
│   └── logback.xml
├── sandbox/                          # Binaires et config JVM Sandbox
├── LICENSE                           # Apache 2.0
├── CONTRIBUTING.md                   # Guide de contribution
├── CHANGELOG.md                      # Journal des modifications
└── README.md
```

## Securite

- `deepcover.properties` est dans `.gitignore` pour eviter les commits accidentels de donnees sensibles
- Les modeles utilisent des espaces reserves `YOUR_*` au lieu d'adresses et cles reelles
- Toutes les dependances sont des composants open source, aucune dependance privee interne

### Dependances

| Dependances | Version | Usage |
|-------------|---------|-------|
| Alibaba JVM Sandbox | 1.4.0 | Framework d'amelioration bytecode |
| OpenTelemetry API | 1.30.0 | Standard de tracage distribue |
| Apache HttpClient | 4.5.6 | Envoi de donnees HTTP |
| Hutool | 5.8.9 | Utilitaires HTTP (rapport au centre de config) |
| FastJSON | 2.0.25 | Serialisation JSON |
| Logback | 1.2.1 | Framework de logging |
| Lombok | 1.18.12 | Simplification du code |
| Kafka Clients | 2.4.1 | Envoi de donnees Kafka |
| Guava | 18.0 | Classes utilitaires |

## Remarques

1. **Impact sur les performances** : La collecte a un cout ; configurez un taux d'echantillonnage approprie en production
2. **Compatibilite SkyWalking** : L'agent DeepCover doit etre charge avant l'agent SkyWalking
3. **Utilisation des ressources** : Les donnees collectees consomment de la memoire ; ajustez `limitCodeMethodSize`
4. **Testez d'abord** : Verifiez completement en environnement de test avant le deploiement en production

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) - Guide de contribution
- [CHANGELOG.md](CHANGELOG.md) - Journal des modifications
- [LICENSE](LICENSE) - Licence Apache 2.0

## Licence

Ce projet est sous licence [Apache License 2.0](LICENSE).

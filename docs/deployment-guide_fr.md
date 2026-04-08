# DeepCover - Guide de deploiement

[中文](deployment-guide.md) | [English](deployment-guide_en.md) | [日本語](deployment-guide_ja.md) | **Francais** | [Portugues](deployment-guide_pt.md) | [Русский](deployment-guide_ru.md)

## Pre-requis

| Composant | Version | Notes |
|-----------|---------|-------|
| Java | 8+ | Recommande: 1.8.0_202+ |
| Maven | 3.5+ | Pour compiler l'agent |
| JVM Sandbox | 1.4.0 | Framework d'instrumentation bytecode |
| Conteneur Servlet | 3.0+ | Tomcat, Jetty, etc. |

## Etape 1: Compiler l'agent DeepCover

```bash
git clone https://github.com/xiaobin1187-git/deepcover.git
cd deepcover
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

Resultat: `target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Etape 2: Installer JVM Sandbox

Telecharger JVM Sandbox depuis le [depot officiel](https://github.com/alibaba/jvm-sandbox) et extraire:

```
sandbox/
├── bin/
│   └── sandbox.sh
├── cfg/
│   └── sandbox.properties
├── lib/
│   ├── sandbox-agent.jar    # javaagent jar
│   └── sandbox-spy.jar
└── sandbox-module/          # Placer le module DeepCover ici
```

## Etape 3: Deployer le module DeepCover

```bash
# Copier l'agent compile dans le repertoire des modules Sandbox
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/
```

## Etape 4: Configurer l'application cible

Ajouter les parametres JVM au script de demarrage de votre application:

### Tomcat

Editer `bin/setenv.sh` (ou `catalina.sh`):

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=nom-de-votre-app \
  -Denv=test \
  -Ddeepcover.env=test \
  -Dbranch=master"
```

### Spring Boot (java -jar)

```bash
java -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
     -Dapp.name=nom-de-votre-app \
     -Denv=test \
     -Ddeepcover.env=test \
     -jar votre-app.jar
```

### Important: Compatibilite SkyWalking

Si vous utilisez SkyWalking APM, les parametres de l'agent DeepCover doivent venir **avant** ceux de SkyWalking:

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \     # DeepCover en premier
-javaagent:/path/to/skywalking-agent.jar \               # SkyWalking en second
```

## Etape 5: Configurer deepcover.properties

Copier et editer le modele de configuration:

```bash
cp src/main/resources/deepcover.properties.example \
   src/main/resources/deepcover.properties
```

Voir le [guide de configuration](configuration-guide_fr.md) pour la description detaillee des parametres.

## Etape 6: Verifier l'installation

### Verifier le chargement du module

```bash
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

Le message de chargement reussi du module doit apparaitre.

### Verifier l'endpoint de metriques

```bash
curl "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/metrics"
```

Reponse JSON attendue avec les statistiques d'execution.

## Etape 7: Controler le cycle de vie du module

```bash
# Activer le module
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# Desactiver le module (reactivable)
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# Decharger completement le module (redemarrage necessaire pour reactiver)
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=nom-de-votre-app"
```

## Depannage

### Le module ne se charge pas

- Verifier le chemin de `sandbox-agent.jar`
- Verifier que les parametres JVM `app.name` et `env` sont definis
- Consulter `~/sandbox/sandbox.log` pour les details d'erreur

### Aucune donnee de couverture collectee

- Verifier que la regex `packageName` correspond aux packages de l'application
- Verifier que la section `env` dans `deepcover.properties` est correcte
- Verifier que le centre de donnees ou Kafka est accessible depuis l'hote

### Impact sur les performances trop eleve

- Reduire `sampleRate` (ex: `5000` pour 50%)
- Abaisser `limitCodeMethodSize` et `limitCodeMethodLineSize`
- Verifier le fonctionnement du disjoncteur via l'endpoint `/metrics`

### Avertissements de file pleine

- Augmenter `queueSize` (defaut: 100)
- Augmenter `queueNum` (defaut: 1)
- Reduire `sampleRate` pour diminuer le volume de donnees

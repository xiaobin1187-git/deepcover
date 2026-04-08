# DeepCover Demo - Application Servlet

[中文](README.md) | [English](README_EN.md) | [日本語](README_ja.md) | **Francais** | [Portugues](README_pt.md) | [Русский](README_ru.md)

Une application web simple demontrant comment DeepCover collecte les donnees de couverture de code.

## Prerequis

- Java 8+
- Maven 3.5+
- Tomcat 8+ (ou tout conteneur Servlet)
- [DeepCover Agent](../../) compile et pret a l'emploi

## Demarrage rapide

### 1. Compiler le demo

```bash
cd examples/demo-servlet
mvn clean package
```

Cela genere `target/demo-servlet.war`.

### 2. Configurer la JVM avec l'agent DeepCover

Ajouter au fichier `catalina.sh` de Tomcat (ou `setenv.sh`) :

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=demo-servlet \
  -Denv=test \
  -Ddeepcover.env=test"
```

### 3. Deploiement et test

```bash
# Copier le WAR dans Tomcat
cp target/demo-servlet.war /path/to/tomcat/webapps/

# Demarrer Tomcat
/path/to/tomcat/bin/startup.sh

# Tester les endpoints
curl "http://localhost:8080/demo-servlet/user?action=list"
curl "http://localhost:8080/demo-servlet/user?action=get&id=1"
curl "http://localhost:8080/demo-servlet/user?action=create&name=Bob&email=bob@example.com"
curl "http://localhost:8080/demo-servlet/user?action=delete&id=1"
```

### 4. Verifier les metriques

```bash
# Afficher les metriques d'execution DeepCover
curl "http://localhost:port/sandbox/default/module/http/deepcover/metrics"
```

## Ce que DeepCover collecte

Lorsque les requetes HTTP atteignent `UserServlet.service()`, DeepCover va :

1. **Tracer la requete** - Enregistrer la methode HTTP, l'URL et le traceId
2. **Instrumenter les appels de methode** - Suivre tous les appels de methode dans le package `io.deepcover.examples.demo.*`
3. **Collecter les numeros de ligne** - Enregistrer quelles lignes de code ont ete executees dans `UserService`
4. **Envoyer les donnees** - Transmettre les donnees de couverture au centre de donnees configure ou au topic Kafka

## Exemple de donnees de couverture

Pour une requete vers `GET /user?action=get&id=1`, DeepCover collecte :

- Classe : `io.deepcover.examples.demo.controller.UserServlet`
- Methode : `service` - lignes executees : 24, 25, 26, 29, 34
- Classe : `io.deepcover.examples.demo.service.UserService`
- Methode : `getUser` - lignes executees : 22, 23, 26, 27

Ces donnees peuvent etre utilisees pour calculer la couverture de code au niveau des lignes par requete HTTP.

## Configuration

Assurez-vous que `deepcover.properties` contient les parametres corrects :

```properties
# Modele de package a collecter
test.packageName=io\\.deepcover\\.examples\\.demo\\..*
```

Ou configurez via le centre de configuration avec `packageName=io\.deepcover\.examples\.demo\..*`.

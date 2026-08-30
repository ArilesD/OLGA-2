# OLGA — API des workflows d'inventaire

OLGA est un système de collecte de données sur le terrain (inventaires de biodiversité, surveillance écologique). Il modélise les saisies sous forme de **workflows** : des graphes de formulaires que les utilisateurs remplissent étape par étape.

**Orchestrator** est la passerelle HTTP qui vous donne accès à l'API OLGA. Vous l'appelez depuis vos propres outils (page web, script, application) pour créer et gérer les workflows, définir les formulaires de saisie et piloter les exécutions.

> Le backend qui exécute réellement le travail est fourni tout prêt, sous forme d'image Docker. Vous n'avez pas à le construire : Orchestrator y est connecté automatiquement au démarrage. La documentation ci-dessous ne suppose aucune connaissance de l'architecture interne.

---

## 1. Présentation de l'API

L'API repose sur trois notions complémentaires :

- **Workflows** — Un workflow est un graphe de nœuds (`start`, `form`, `save`, `end`) reliés par des arêtes. Il décrit le déroulement d'une collecte : chaque `form` est une étape de saisie, `save` déclenche la persistance. Les workflows sont versionnés : vous pouvez consulter l'historique et restaurer une version précédente.

- **Formulaires** — Un formulaire définit les champs d'une étape de saisie (`field_key`, `field_label`, `field_type`, etc.). Les formulaires sont réutilisables et associés aux nœuds des workflows.

- **Exécutions** — Lancer un workflow sur un inventaire crée une **tâche** : le système remplit progressivement les formulaires, soumet les données et avance dans le graphe jusqu'à la fin.

Concrètement, vous pouvez :

- créer, charger, modifier et versionner des workflows ;
- gérer vos formulaires (création, lecture, mise à jour, suppression) ;
- lister les inventaires accessibles à un utilisateur et les tâches en cours ;
- démarrer une collecte, soumettre chaque formulaire et reprendre une tâche là où elle s'est arrêtée.

---

## 2. Démarrage rapide

### 2.1 Prérequis

- **Docker** (avec Docker Compose v2) sur votre machine.
- **La clé de service Firebase** du backend (voir 2.2). C'est le seul fichier de configuration à obtenir, en dehors de Git.

### 2.2 Préparer la clé Firebase

Le backend s'appuie sur Firestore (Firebase) pour certaines données métier. La clé de service Firebase est obligatoire, mais elle n'est **jamais** stockée dans ce repository : elle est montée dans le conteneur au démarrage.

1. Obtenez la clé (fichier JSON de type *service account*) auprès de votre équipe ou de l'administrateur du projet.
2. Créez un dossier `firebase/` à la racine et placez-y la clé en la nommant exactement **`apiKey.json`** :

```
firebase/apiKey.json
```

Ce fichier est ignoré par Git (`firebase/apiKey.json`) : il ne doit jamais être versionné. Sans lui, le backend ne démarre pas correctement.

### 2.3 Lancer le projet

Depuis la racine du repository :

```bash
docker compose up --build -d
```

Au premier lancement, Docker télécharge l'image du backend et construit Orchestrator : c'est automatique. Pour suivre le démarrage et vérifier que tout est prêt :

```bash
docker compose ps
```

Vous devez voir les trois services au statut `healthy` : `olga-db`, `olga-backend` et `olga-orchestrator`.

### 2.4 Vérifier que l'API répond

```bash
curl http://localhost:9092/health
```

Réponse attendue :

```json
{"status":"UP"}
```

Vous êtes prêt. Passez à la section suivante pour découvrir l'API.

---

## 3. Utiliser l'API

### 3.1 URL de base et documentation

L'API est accessible une fois le projet démarré à l'adresse :

```
http://localhost:9092
```

La **documentation de référence** de tous les endpoints (paramètres, formats, exemples de requêtes et de réponses) est consultable dans l'interface Swagger :

```
http://localhost:9092/swagger-ui.html
```

> Considérez Swagger comme la source de vérité : chaque endpoint y est détaillé. Le README vous donne l'essentiel pour démarrer ; pour les aspects précis (formats exacts, champs facultatifs), ouvrez Swagger.

### 3.2 Les grandes familles d'API

| Famille | Base | Rôle |
|---|---|---|
| Workflows | `/workflows` | Lire, créer, sauvegarder et versionner les workflows |
| Forms | `/forms` | Gérer les formulaires (CRUD) |
| Execute | `/execute` | Piloter l'exécution des workflows (tâches) |

### 3.3 Premiers pas

**Lister les workflows :**

```bash
curl http://localhost:9092/workflows
```

**Charger un workflow complet (nœuds et arêtes) :**

```bash
curl http://localhost:9092/workflows/wf_abc123
```

**Sauvegarder un workflow :** le corps doit contenir un champ `workflow` (le backend crée une version avant d'écraser) :

```bash
curl -X PUT http://localhost:9092/workflows/wf_abc123 \
  -H 'Content-Type: application/json' \
  -d '{
        "edited_by": "mon-app",
        "workflow": {
          "nodes": [
            { "id": "n1", "type": "start", "position": { "x": 100, "y": 100 }, "data": {} },
            { "id": "n2", "type": "form", "position": { "x": 300, "y": 100 },
              "data": { "form_id": "Form1", "form_label": "Consultation", "form_groups": ["medecins"] } }
          ],
          "edges": [ { "id": "e1", "source": "n1", "target": "n2", "animated": true } ]
        }
      }'
```

**Explorer une exécution :** le cycle typique est : lister les inventaires accessibles, démarrer une tâche, puis soumettre les formulaires l'un après l'autre.

```bash
# Inventaires accessibles à un utilisateur (avec l'info "canStart")
curl "http://localhost:9092/execute/inventories?email=user@example.com"

# Démarrer une tâche sur un inventaire -> renvoie le premier formulaire
curl "http://localhost:9092/execute/start?inventory_id=INV001&email=user@example.com"

# Soumettre les données du formulaire courant -> renvoie le suivant
curl -X POST "http://localhost:9092/execute/next?task_id=abc123taskDocId&email=user@example.com" \
  -H 'Content-Type: application/json' \
  -d '{"lastname1": "Dupont", "Taille1": "170"}'
```

---

## 4. Documentation des endpoints

La description exhaustive de chaque endpoint (méthode, paramètres, exemples, codes de réponse) est générée automatiquement et consultable dans **Swagger** après le démarrage :

```
http://localhost:9092/swagger-ui.html
```

Pour vous repérer, voici l'organisation des endpoints par famille.

### Workflows — `/workflows`

| Méthode | Endpoint | Rôle |
|---|---|---|
| GET | `/workflows` | Lister les workflows (métadonnées) |
| GET | `/workflows/{workflowId}` | Charger un workflow complet (nœuds + arêtes) |
| PUT | `/workflows/{workflowId}` | Sauvegarder un workflow (le champ `workflow` est obligatoire) |
| GET | `/workflows/{workflowId}/versions` | Lister l'historique des versions |
| POST | `/workflows/{workflowId}/versions/{versionId}/restore` | Restaurer une version précédente |

### Formulaires — `/forms`

| Méthode | Endpoint | Rôle |
|---|---|---|
| GET | `/forms` | Lister les formulaires (id, label, groupes) |
| GET | `/forms/{formId}` | Charger le détail complet d'un formulaire |
| POST | `/forms/save` | Créer un formulaire |
| POST | `/forms/update/{formId}` | Mettre à jour un formulaire existant |
| DELETE | `/forms/{formId}` | Supprimer un formulaire |

### Exécution — `/execute`

| Méthode | Endpoint | Rôle |
|---|---|---|
| GET | `/execute/inventories?email=` | Inventaires accessibles à l'utilisateur (info `canStart`) |
| GET | `/execute/start?inventory_id=&email=` | Démarrer une tâche, renvoie le premier formulaire |
| POST | `/execute/next?task_id=&email=` | Soumettre le formulaire courant, recevoir le suivant |
| GET | `/execute/status?task_id=` | État d'une tâche (reprise sans modification) |
| GET | `/execute/tasks?email=` | Tâches démarrées par un utilisateur |

> Tous les chemins et noms de paramètres ci-dessus correspondent **exactement** au Swagger généré (ex. `inventory_id`, `email`, `task_id`, `workflowId`, `formId`). En cas de doute sur le format d'un champ, ouvrez Swagger.

---

## 5. Configuration et dépannage

### 5.1 Configuration

Les valeurs par défaut permettent un démarrage immédiat. Vous pouvez les ajuster dans un fichier `.env`, copié depuis `.env.example` :

| Variable | Défaut | Rôle |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `rootpassword` | Mot de passe MySQL réservé au backend |
| `MYSQL_DATABASE` | `olga` | Nom de la base de données |
| `ORCHESTRATOR_PORT` | `9092` | Port d'exposition de l'API sur votre machine |
| `BACKEND_IMAGE` | `ghcr.io/nexus-ai-innovation-lab/olga-backend:1.0.0` | Image Docker du backend à télécharger |

### 5.2 Dépannage

**Le backend ne devient jamais `healthy`.**
C'est presque toujours la clé Firebase. Vérifiez que `firebase/apiKey.json` existe et est valide (section 2.2), puis consultez les journaux :

```bash
docker compose logs -f backend
```

**L'API répond `502 Bad Gateway`.**
Le backend a renvoyé une erreur ou est injoignable. Regardez l'état des services et les journaux :

```bash
docker compose ps
docker compose logs -f backend
```

**Le port 9092 est déjà occupé.**
```bash
ORCHESTRATOR_PORT=9192 docker compose up -d
```

**Impossible de télécharger l'image du backend (`denied` ou `manifest unknown`).**
L'image est hébergée sur GitHub Container Registry (GHCR). Si elle est privée, authentifiez-vous :

```bash
echo "<TOKEN>" | docker login ghcr.io -u <votre-username> --password-stdin
```

Le token (avec le droit `read:packages`) est fourni par votre équipe. Ne le partagez jamais dans le code ni dans Git.

**Réinitialiser la base de données.**
```bash
docker compose down -v
```
⚠️ `-v` supprime aussi le volume de données : les données locales sont effacées.

**Arrêter l'ensemble.**
```bash
docker compose down
```

---

## 6. Pour les mainteneurs

Cette section ne concerne que les personnes chargées de publier une nouvelle version de l'image backend. Les utilisateurs de l'API n'en ont pas besoin.

Le backend est distribué uniquement sous forme d'image Docker (jamais en code source). La clé Firebase n'est **pas** incluse dans l'image : elle est montée au runtime, ce qui autorise une distribution sûre. Pour publier une nouvelle version, depuis le dépôt qui contient le code source du backend :

```bash
docker build -f Dockerfile.olga -t ghcr.io/nexus-ai-innovation-lab/olga-backend:<tag> .
docker push ghcr.io/nexus-ai-innovation-lab/olga-backend:<tag>
```

Ensuite, référencez le nouveau tag dans le `compose.yaml` (variable `BACKEND_IMAGE`) pour que les développeurs obtiennent la nouvelle version de façon reproductible. Utilisez un tag de version explicite plutôt que `latest`.

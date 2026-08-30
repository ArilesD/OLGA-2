# OLGA — Workflows & Orchestrator

OLGA est un système de collecte de données sur le terrain (inventaires de biodiversité) utilisé pour le suivi écologique. Ce repository vous permet d'utiliser **Orchestrator**, la passerelle REST qui expose l'API des workflows, et de la connecter au **backend** qui exécute réellement le travail.

Le backend (OLGA MultiServices + Workflows) n'est **pas distribué en code source** : il est fourni sous forme d'**image Docker** publiée sur GitHub Container Registry (GHCR) et tirée automatiquement au démarrage. Votre repository contient uniquement l'Orchestrator (le code source que vous utilisez et adaptez) et la configuration d'orchestration.

```
Développeur
     │   requêtes HTTP
     ▼
 Orchestrator  (:9092)   ← code source présent dans ce repository
     │   communication interne (réseau Docker)
     ▼
   Backend  (:9091)      ← image Docker tirée de GHCR, pas de code source
```

Ce que vous pouvez faire avec Orchestrator :

- **Créer et modifier des workflows** : un workflow est un graphe de nœuds (`start`, `form`, `save`, `end`) reliés par des arêtes, avec versioning.
- **Gérer des formulaires** : les formulaires définissent les champs de saisie associés aux nœuds d'un workflow.
- **Exécuter des workflows** : démarrer une tâche sur un inventaire, remplir les formulaires étape par étape, suivre et reprendre des tâches.

---

## 1. L'API

Orchestrator expose trois familles d'endpoints, toutes en JSON :

| Famille | Base | Rôle |
|---|---|---|
| Workflows | `/workflows` | Lire, charger, sauvegarder, versionner les workflows |
| Formulaires | `/forms` | CRUD des formulaires |
| Exécution | `/execute` | Démarrer et suivre l'exécution des workflows |

La **documentation interactive complète** (OpenAPI / Swagger) est disponible une fois le projet démarré :

```
http://localhost:9092/swagger-ui.html
```

Vous y trouverez chaque endpoint, ses paramètres, ses exemples de requêtes et de réponses.

---

## 2. Prérequis

- **Docker** (Desktop sur Windows/Mac, ou le moteur Docker + Compose v2 sur Linux).
- **La clé de service Firebase** du backend (voir la section 4).
- Aucune connaissance du fonctionnement interne du backend n'est nécessaire.

---

## 3. Récupérer le projet

Clonez ce repository :

```bash
git clone <url-du-repository> && cd <dossier>
```

Le repository contient :

```
.
├── compose.yaml          ← orchestration Docker (backend image + orchestrator)
├── Orchestrator/         ← code source de la passerelle (édition, execution)
├── README.md
└── .env.example          ← modèle de configuration
```

Le dossier `Backend-2/` n'y figure **pas** : le backend est récupéré automatiquement sous forme d'image Docker, pas de code source.

---

## 4. Préparer la clé Firebase

Le backend utilise Firestore (Firebase) pour ses besoins métier. La clé de service Firebase est **obligatoire**, mais elle n'est **jamais** intégrée à l'image ni au repository : elle est montée dans le conteneur au démarrage.

> **Où trouver la clé ?** La clé de service Firebase (fichier JSON de type "service account") est fournie par votre équipe / le mainteneur du backend. Elle est délivrée en dehors du Git et ne doit pas circuler par ce canal. Si vous ne l'avez pas, demandez-la à l'administrateur du projet avant de continuer.

**Étape 1 — Où la placer :** créez un dossier `firebase/` à la racine du projet :

```
firebase/
```

**Étape 2 — Le nom du fichier :** déposez la clé en la nommant exactement **`apiKey.json`** :

```
firebase/apiKey.json
```

**Étape 3 — Comment Docker la monte :** au démarrage, le `compose.yaml` monte ce fichier **en lecture seule** dans le conteneur du backend, au chemin attendu par l'application :

```yaml
volumes:
  - ./firebase/apiKey.json:/app/config/apiKey.json:ro
```

L'application (via la variable `FIREBASE_KEY_PATH=/app/config/apiKey.json`) lit la clé à cet endroit au lancement.

**Si le fichier est absent :** le backend ne pourra pas initialiser Firestore et **ne démarrera pas correctement** (il échouera ou restera en panne du point de vue des healthchecks). Vérifiez donc que `firebase/apiKey.json` existe et est valide avant de lancer.

> Ce fichier est listé dans le `.gitignore` (`firebase/apiKey.json`) : il ne doit jamais être versionné.

---

## 5. Démarrer l'ensemble

Depuis la racine du projet :

```bash
docker compose up --build
```

> `-d` pour lancer en arrière-plan : `docker compose up --build -d`.
> `--profile tools` pour démarrer aussi phpMyAdmin (outil de dev facultatif) : `docker compose up --build --profile tools`.

Au premier lancement, Docker **tire l'image du backend** depuis GHCR (`ghcr.io/nexus-ai-innovation-lab/olga-backend:1.0.0`) et **construit l'Orchestrator** depuis le code source local. C'est automatique, il n'y a rien d'autre à faire.

### Vérifier que tout est prêt

```bash
docker compose ps
```

Les trois services doivent être `healthy` :

- `olga-db` — MySQL
- `olga-backend` — le backend (image GHCR)
- `olga-orchestrator` — la passerelle publique

---

## 6. Accéder à l'API

| Ressource | Adresse |
|---|---|
| API Orchestrator | `http://localhost:9092` |
| Swagger UI | `http://localhost:9092/swagger-ui.html` |
| Healthcheck | `http://localhost:9092/health` |
| phpMyAdmin (profil `tools`) | `http://localhost:8081` |

Seul le port **9092** (Orchestrator) est exposé sur votre machine. Le backend (9091) et MySQL (3306) restent sur le réseau Docker interne : ils ne sont pas accessibles depuis l'extérieur.

---

## 7. Utiliser les principaux endpoints

Toutes les requêtes sont en JSON.

### Lister les workflows

```bash
curl http://localhost:9092/workflows
```

```json
[
  { "workflow_id": "wf_abc123", "workflow_label": "Mon Workflow", "from_web": true, "last_updated": "..." }
]
```

### Charger un workflow complet

```bash
curl http://localhost:9092/workflows/wf_abc123
```

### Sauvegarder un workflow

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

### Exécuter un workflow

```bash
curl "http://localhost:9092/execute/inventories?email=user@example.com"
curl "http://localhost:9092/execute/start?inventory_id=INV001&email=user@example.com"
curl -X POST "http://localhost:9092/execute/next?task_id=abc123&email=user@example.com" \
  -H 'Content-Type: application/json' \
  -d '{"lastname1": "Dupont", "Taille1": "170"}'
curl "http://localhost:9092/execute/tasks?email=user@example.com"
curl "http://localhost:9092/execute/status?task_id=abc123"
```

---

## 8. Configuration

Les valeurs par défaut conviennent à un démarrage local immédiat. Vous pouvez les surcharger via un fichier `.env` copié depuis `.env.example`, ou via les variables d'environnement de votre machine.

| Variable | Défaut | Rôle |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `rootpassword` | Mot de passe MySQL du backend |
| `MYSQL_DATABASE` | `olga` | Nom de la base MySQL |
| `ORCHESTRATOR_PORT` | `9092` | Port exposé de l'Orchestrator |
| `BACKEND_IMAGE` | `ghcr.io/nexus-ai-innovation-lab/olga-backend:1.0.0` | Image Docker du backend à tirer |

L'Orchestrator gère aussi `SERVER_PORT` et `OLGA_BACKEND_BASE_URL` ; le backend gère les variables internes (`ADDRESS`, `PORT`, `MYSQL_*`, `FIREBASE_KEY_PATH`). En Docker, ces valeurs sont déjà câblées dans le `compose.yaml` — rien à configurer pour un usage normal.

---

## 9. Problèmes courants

**Le backend ne devient jamais `healthy`.**
Vérifiez d'abord que `firebase/apiKey.json` existe et est valide (section 4). Consultez ensuite les journaux :

```bash
docker compose logs -f backend
```

**L'Orchestrator répond `502 Bad Gateway`.**
Le backend a renvoyé une erreur (ou est injoignable). Regardez `docker compose ps` et les journaux du backend.

**Le port 9092 est déjà pris.**
```bash
ORCHESTRATOR_PORT=9192 docker compose up -d
```

**Erreur de pull de l'image backend (`manifest unknown` ou `denied`).**
Vous devez être authentifié auprès de GHCR pour télécharger l'image :
```bash
echo "<TOKEN>" | docker login ghcr.io -u <votre-username> --password-stdin
```
Le token (classic PAT avec `read:packages`) vous est fourni par votre équipe. Rappel : ne partagez jamais ce token dans le code ni dans Git.

**Réinitialiser les données MySQL.**
```bash
docker compose down -v
```
⚠️ `-v` supprime aussi le volume `olga_data` : les données MySQL locales sont effacées.

**Arrêter l'ensemble.**
```bash
docker compose down
```

**Mettre à jour l'image du backend.**
Si votre équipe publie une nouvelle version de l'image, indiquez son tag (ex. `BACKEND_IMAGE=ghcr.io/nexus-ai-innovation-lab/olga-backend:1.1.0 docker compose up -d`) ou forcez le retirage :
```bash
docker compose pull backend
```

---

## 10. Pour les mainteneurs : publier l'image du backend

Le backend est une **image privée** construite depuis un dépôt séparé (non publié aux développeurs). Pour publier une nouvelle version, depuis le dépôt du backend (celui qui contient le code source) :

```bash
docker build -f Dockerfile.olga -t ghcr.io/nexus-ai-innovation-lab/olga-backend:1.0.0 .
docker push ghcr.io/nexus-ai-innovation-lab/olga-backend:1.0.0
```

L'image ne contient **aucune donnée sensible** (la clé Firebase est fournie au runtime), ce qui permet de la distribuer en toute sécurité. Tant que le tag utilisé par `compose.yaml` (voir `BACKEND_IMAGE`) n'est pas modifié, les développeurs continuent d'obtenir une version **reproductible** : évitez de référencer `latest`.

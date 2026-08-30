# Orchestrateur — API d'édition et d'exécution de workflows

Façade REST qui proxyfie le backend Olga. Point d'entrée unique pour :
- **Éditer** des workflows visuellement (drag & drop, versioning)
- **Gérer** des formulaires (CRUD)
- **Exécuter** des workflows (démarrer une tâche, soumettre des formulaires, avancer dans le graphe)

```
┌──────────────┐     ┌──────────────┐     ┌──────────┐
│  App externe  │────▶│ Orchestrateur │────▶│  Backend  │
│  (HTML/JS)    │◀────│  :9092       │◀────│  :9091    │
└──────────────┘     └──────────────┘     └──────────┘
```

Tous les endpoints sont en CORS ouvert → accessibles depuis n'importe quel contexte (page `file://`, application web, script CLI).

---

## Démarrage

```bash
cd Orchestrator
./gradlew bootRun
```

L'orchestrateur tourne sur `http://localhost:9092`.  
Le backend Olga doit être accessible (par défaut `http://localhost:9091`).

## Configuration

`app/src/main/resources/application.properties` :

```properties
server.port=9092
olga.backend.base-url=http://localhost:9091
```

---

## API — Endpoints

### 📋 Workflows (édition)

| Méthode | Path | Description |
|---------|------|-------------|
| `GET` | `/workflows` | Liste tous les workflows |
| `GET` | `/workflows/{id}` | Charge un workflow complet (nœuds + liens) |
| `PUT` | `/workflows/{id}` | Sauvegarde (crée une version avant) |
| `GET` | `/workflows/{id}/versions` | Liste les versions |
| `POST` | `/workflows/{id}/versions/{vid}/restore` | Restaure une version |

#### GET /workflows

```json
[
  { "workflow_id": "wf_abc123", "workflow_label": "Mon Workflow", "from_web": true, "last_updated": "..." }
]
```

#### GET /workflows/{id}

```json
{
  "workflow_id": "wf_abc123",
  "workflow_label": "Mon Workflow",
  "nodes": [
    { "id": "n1", "type": "start", "position": { "x": 100, "y": 100 }, "data": {} },
    { "id": "n2", "type": "form", "position": { "x": 300, "y": 100 }, "data": { "form_id": "f1", "form_label": "Demande", "form_groups": ["group1"] } },
    { "id": "n3", "type": "save", "position": { "x": 500, "y": 100 }, "data": { "collectionLabel": "Rapports", "collectionId": "col1" } },
    { "id": "n4", "type": "end", "position": { "x": 700, "y": 100 }, "data": {} }
  ],
  "edges": [
    { "id": "e1", "source": "n1", "target": "n2", "animated": true },
    { "id": "e2", "source": "n2", "target": "n3", "animated": true },
    { "id": "e3", "source": "n3", "target": "n4", "animated": true }
  ]
}
```

#### PUT /workflows/{id}

```json
// Requête
{ "edited_by": "editor", "workflow": { "nodes": [...], "edges": [...] } }

// Réponse
{ "document_id": "doc_xxx", "workflow_id": "wf_abc123" }
```

### 📝 Formulaires

| Méthode | Path | Description |
|---------|------|-------------|
| `GET` | `/forms` | Liste minimaliste (id, label, groupes) |
| `GET` | `/forms/{id}` | Détail complet (champs, règles) |
| `POST` | `/forms/save` | Crée un formulaire |
| `POST` | `/forms/update/{id}` | Met à jour |
| `DELETE` | `/forms/{id}` | Supprime |

### ▶️ Exécution de workflows

| Méthode | Path | Description |
|---------|------|-------------|
| `GET` | `/execute/inventories` | Inventaires accessibles à l'utilisateur |
| `GET` | `/execute/start` | Démarre une tâche → premier formulaire |
| `POST` | `/execute/next` | Soumet un formulaire → suivant ou fin |
| `GET` | `/execute/status` | État d'une tâche (reprise) |
| `GET` | `/execute/tasks` | Tâches démarrées par l'utilisateur |

---

#### GET /execute/inventories?email=user@example.com

Retourne les inventaires où l'utilisateur a accès, avec l'info `canStart`.

```json
[
  {
    "inventory_code": "INV001",
    "name": "Hôpital Nord",
    "workflows": ["wf_abc123"],
    "groupes": { "medecins": ["user@example.com"] },
    "canStart": true
  }
]
```

#### GET /execute/start?inventory_id=INV001&email=user@example.com

Démarre une tâche d'exécution. Le backend crée un document task en Firestore, trouve le premier nœud `form` du workflow, et retourne le formulaire complet.

```json
{
  "task_id": "abc123taskDocId",
  "form": {
    "form_id": "Form3_RDVG",
    "form_label": "Consultation",
    "form_version": "1.0.0",
    "form_category": "protocol",
    "form": [
      {
        "field_key": "lastname1",
        "field_label": "Nom",
        "field_type": "input:text",
        "field_hint": "Dupont",
        "field_required": false,
        "unique_id": "2c795f03-..."
      },
      {
        "field_key": "Taille1",
        "field_label": "Taille (cm)",
        "field_type": "input:number",
        "field_hint": "ex: 170",
        "field_required": false,
        "unique_id": "8072a8d8-..."
      },
      {
        "field_key": "MotifsVisite1",
        "field_label": "Motifs de la visite",
        "field_type": "select",
        "field_options": { "options": [{ "label": "Fièvre" }, { "label": "Douleurs" }] },
        "field_hint": "",
        "unique_id": "a9ac9783-..."
      },
      {
        "field_key": "Observations1",
        "field_label": "Observations",
        "field_type": "textarea",
        "field_hint": "",
        "field_required": true,
        "unique_id": "d7e562fa-..."
      },
      {
        "field_key": "ConsultationValidee",
        "field_label": "Consultation validée",
        "field_type": "checkbox",
        "unique_id": "9e9182fa-..."
      }
    ]
  }
}
```

Les types de champs supportés :

| `field_type` | Rendu attendu |
|---|---|
| `input:text` | champ texte |
| `input:number` | champ numérique |
| `textarea` | zone de texte multiligne |
| `select` | menu déroulant (via `field_options.options[]`) |
| `checkbox` | case à cocher |

#### POST /execute/next?task_id=abc123&email=user@example.com

Soumet les données saisies et avance dans le workflow.

```json
// Requête — les clés sont les field_key du formulaire
{ "lastname1": "Dupont", "Taille1": "170", "MotifsVisite1": "Fièvre" }

// Réponse — formulaire suivant, ou form: null si terminé
{ "form": { ... } }
// ou
{ "form": null }
// ou si accès refusé pour le groupe suivant
{ "form": null, "next_groups": ["specialistes"] }
```

Ce que fait le backend :
1. Fusionne les données reçues dans `task.data`
2. Parcourt le graphe du workflow (edges) depuis le nœud courant
3. Exécute les nœuds `save` (persiste les données dans la collection Firestore dédiée)
4. Retourne le prochain nœud `form` (ou `null` si fin)
5. Vérifie les droits via `form_groups` — si l'utilisateur n'est pas dans le groupe, retourne `next_groups` sans formulaire

#### GET /execute/status?task_id=abc123

Reprend une tâche sans la modifier.

```json
{ "form": { ... }, "data": { "lastname1": "Dupont", ... } }
```

#### GET /execute/tasks?email=user@example.com

```json
[
  { "id": "abc123", "inventory_code": "INV001", "current_node_id": "n2", "startedBy": "user@example.com", "created_at": { "seconds": 1700000000 } }
]
```

---

## Cycle de vie complet d'une exécution

```
1. GET  /execute/inventories?email=X          → liste des inventaires
2. GET  /execute/start?inventory_id=Y&email=X → {task_id, form}
3. POST /execute/next?task_id=Z&email=X       → {form}  (autant de fois que d'étapes)
   body: { field_key: valeur, ... }
4. POST /execute/next...                       → {form: null} → workflow terminé
```

Pour reprendre une tâche existante :

```
1. GET /execute/tasks?email=X                 → liste des tâches
2. GET /execute/status?task_id=Z              → {form, data} → continuer à l'étape 3
```

---

## Utilisation depuis une application externe

Peu importe le langage ou le contexte (page HTML, script Node.js, curl, application mobile), l'orchestrateur expose une API REST standard.

### Exemple JavaScript (fetch)

```js
const API = 'http://localhost:9092';

// Édition
const list = await fetch(`${API}/workflows`).then(r => r.json());
const wf = await fetch(`${API}/workflows/wf_abc123`).then(r => r.json());
await fetch(`${API}/workflows/wf_abc123`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ edited_by: 'my-app', workflow: { nodes: [], edges: [] } })
});

// Exécution
const invs = await fetch(`${API}/execute/inventories?email=user@test.com`).then(r => r.json());
const { task_id, form } = await fetch(`${API}/execute/start?inventory_id=${invs[0].inventory_code}&email=user@test.com`).then(r => r.json());
const { form: next } = await fetch(`${API}/execute/next?task_id=${task_id}&email=user@test.com`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ lastname1: "Dupont" })
}).then(r => r.json());
```

### Exemple curl

```bash
# Inventaires
curl "http://localhost:9092/execute/inventories?email=user@test.com"

# Démarrer une tâche
curl "http://localhost:9092/execute/start?inventory_id=INV001&email=user@test.com"

# Soumettre un formulaire
curl -X POST "http://localhost:9092/execute/next?task_id=abc123&email=user@test.com" \
  -H 'Content-Type: application/json' \
  -d '{"lastname1":"Dupont","Taille1":"170"}'

# Voir l'état
curl "http://localhost:9092/execute/status?task_id=abc123"

# Mes tâches
curl "http://localhost:9092/execute/tasks?email=user@test.com"
```

---

## Structure attendue d'un nœud de workflow

```json
{
  "id": "n_unique_id",
  "type": "start|end|form|save",
  "position": { "x": 100, "y": 200 },
  "data": {
    "form_id": "f1",          // type=form
    "form_label": "Demande",  // type=form
    "form_groups": ["grp1"],  // type=form — groupes autorisés à remplir
    "collectionLabel": "",    // type=save
    "collectionId": ""        // type=save
  }
}
```

> Les champs `data.id`, `data.type`, `data.form_actors` (tableau vide) doivent être présents si l'application principale les attend.

## Structure d'un formulaire en Firestore

```json
{
  "form_id": "Form3_RDVG",
  "form_label": "Consultation",
  "form_version": "1.0.0",
  "form_category": "protocol",
  "last_updated": "2024-12-30",
  "form": [
    {
      "field_key": "lastname1",
      "field_label": "Nom",
      "field_type": "input:text",
      "field_hint": "Dupont",
      "field_required": false,
      "field_mode": "edit",
      "unique_id": "2c795f03-..."
    }
  ],
  "models": ["Patient", "Infirmier"]
}
```

---

## Pages HTML de test

Deux pages HTML autonomes sont fournies à la racine du projet :

### `workflow-editor.html` — Éditeur visuel

```bash
python -m http.server 8080
# → http://localhost:8080/workflow-editor.html
```

Fonctionnalités : drag & drop de nœuds, mode "Lier", versionnage, sauvegarde.

### `workflow-execution-test.html` — Test d'exécution

Ouvrir directement en `file://` (CORS déjà ouvert côté serveur).

Fonctionnalités :
- Charger les inventaires accessibles
- Démarrer une tâche → voir le formulaire avec ses champs typés
- Remplir et soumettre les formulaires étape par étape
- Visualiser les données accumulées
- Reprendre une tâche existante

---

## Développement

```bash
# Compiler
./gradlew compileJava

# Lancer
./gradlew bootRun

# Tests
./gradlew test
```

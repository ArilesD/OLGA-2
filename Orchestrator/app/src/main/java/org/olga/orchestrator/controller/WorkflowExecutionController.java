package org.olga.orchestrator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.olga.orchestrator.client.WorkflowBackendClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Façade d'exécution de workflows.
 *
 * <p>Ces endpoints sont des proxies purs qui délèguent au backend Olga. L'orchestrateur
 * ne modifie ni n'inspecte les réponses : il les transmet telles quelles au client.
 * Cela permet aux clients (page HTML, script curl, application externe) de dialoguer
 * avec un point d'entrée unique sans connaître l'architecture interne.</p>
 *
 * <p>Le cycle d'exécution typique est :</p>
 * <ol>
 *   <li><b>GET /execute/inventories</b> — liste les inventaires accessibles à l'utilisateur</li>
 *   <li><b>GET /execute/start</b> — démarre une tâche sur un inventaire, retourne le 1er formulaire</li>
 *   <li><b>POST /execute/next</b> — soumet les données du formulaire courant, reçoit le suivant</li>
 *   <li><b>GET /execute/status</b> — reprend une tâche existante et obtient son état</li>
 *   <li><b>GET /execute/tasks</b> — liste les tâches démarrées par un utilisateur</li>
 * </ol>
 *
 * <p>Tous les endpoints sont en CORS ouvert pour permettre les appels depuis des pages
 * servies en file:// ou des environnements de test.</p>
 */
@RestController
@CrossOrigin
@RequestMapping("/execute")
@Tag(name = "Exécution", description = "Exécution des workflows : inventaires, démarrage, progression, reprise.")
public class WorkflowExecutionController {

    private final WorkflowBackendClient backendClient;

    public WorkflowExecutionController(WorkflowBackendClient backendClient) {
        this.backendClient = backendClient;
    }

    /**
     * Démarre une nouvelle tâche d'exécution pour un inventaire.
     *
     * <p>Le backend crée un document de tâche dans Firestore, associe le premier
     * nœud de type "form" du workflow, et retourne le formulaire correspondant
     * avec ses champs (field_key, field_label, field_type, etc.).
     *
     * @param inventory_id identifiant de l'inventaire (code ou ID Firestore)
     * @param email        email de l'utilisateur qui lance la tâche
     * @return JSON { task_id, form } où form est la structure complète du premier formulaire
     */
    @Operation(summary = "Démarrer une tâche sur un inventaire",
            description = "Crée une tâche d'exécution et retourne le premier formulaire du workflow associé à l'inventaire.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tâche démarrée avec le premier formulaire",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"task_id\":\"abc123taskDocId\",\"form\":{\"form_id\":\"Form3_RDVG\",\"form_label\":\"Consultation\",\"form_version\":\"1.0.0\",\"form_category\":\"protocol\",\"form\":[{\"field_key\":\"lastname1\",\"field_label\":\"Nom\",\"field_type\":\"input:text\",\"field_hint\":\"Dupont\",\"field_required\":false,\"unique_id\":\"2c795f03\"}]}}"))),
            @ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"error\":\"Bad Gateway\",\"details\":\"Backend unavailable\"}")))
    })
    @GetMapping(value = "/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> startTask(
            @Parameter(description = "Identifiant de l'inventaire (code ou ID Firestore)", required = true, example = "INV001")
            @RequestParam String inventory_id,
            @Parameter(description = "Email de l'utilisateur qui lance la tâche", required = true, example = "user@example.com")
            @RequestParam String email) {
        try {
            return ResponseEntity.ok(backendClient.startTask(inventory_id, email));
        } catch (Exception e) {
            return badGateway(e);
        }
    }

    /**
     * Soumet les données du formulaire en cours et avance au suivant.
     *
     * <p>Le backend fusionne les données envoyées avec le champ "data" de la tâche,
     * puis parcourt le graphe du workflow (edges) pour trouver le prochain nœud
     * de type "form". Si un nœud "save" est rencontré, les données sont persistées
     * dans la collection Firestore dédiée via saveInCollection().
     *
     * <p>Le body doit être un objet JSON plat { field_key: valeur, ... }.
     *
     * @param task_id identifiant de la tâche en cours
     * @param email   email de l'utilisateur qui soumet
     * @param body    données du formulaire (objet clé/valeur)
     * @return JSON { form } ou { form: null } si le workflow est terminé
     */
    @Operation(summary = "Soumettre un formulaire et avancer",
            description = "Soumet les données du formulaire courant (objet clé/valeur) et reçoit le formulaire suivant, ou {form: null} si le workflow est terminé, ou {form: null, next_groups} si l'accès au groupe suivant est refusé.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Formulaire suivant (ou fin du workflow)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"form\":{...} }"))),
            @ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
    })
    @PostMapping(value = "/next", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> nextTask(
            @Parameter(description = "Identifiant de la tâche en cours", required = true, example = "abc123taskDocId")
            @RequestParam String task_id,
            @Parameter(description = "Email de l'utilisateur qui soumet", required = true, example = "user@example.com")
            @RequestParam String email,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Données du formulaire à soumettre (objet clé/valeur)",
                    required = false,
                    content = @Content(examples = @ExampleObject(value = "{\"lastname1\":\"Dupont\",\"Taille1\":\"170\",\"MotifsVisite1\":\"Fièvre\"}")))
            @RequestBody(required = false) String body) {
        try {
            return ResponseEntity.ok(backendClient.nextTask(task_id, email, body == null ? "{}" : body));
        } catch (Exception e) {
            return badGateway(e);
        }
    }

    /**
     * Interroge l'état d'une tâche sans la modifier.
     *
     * <p>Utile pour reprendre une tâche après rechargement de la page : retourne
     * le formulaire en cours (ou null si terminée) ainsi que les données accumulées.
     *
     * @param task_id identifiant de la tâche
     * @return JSON { form, data } où data est l'objet des données déjà collectées
     */
    @Operation(summary = "État d'une tâche (reprise)",
            description = "Retourne le formulaire en cours (ou null si la tâche est terminée) et les données déjà collectées, sans modifier la tâche.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "État de la tâche",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"form\":{...},\"data\":{\"lastname1\":\"Dupont\"}}"))),
            @ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
    })
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getStatus(
            @Parameter(description = "Identifiant de la tâche", required = true, example = "abc123taskDocId")
            @RequestParam String task_id) {
        try {
            return ResponseEntity.ok(backendClient.getTaskStatus(task_id));
        } catch (Exception e) {
            return badGateway(e);
        }
    }

    /**
     * Liste les tâches démarrées par un utilisateur donné.
     *
     * @param email email de l'utilisateur
     * @return tableau JSON des tâches (id, inventory_code, current_node_id, startedBy, created_at)
     */
    @Operation(summary = "Lister les tâches démarrées par un utilisateur",
            description = "Retourne les tâches (id, inventory_code, current_node_id, startedBy, created_at) démarrées par l'utilisateur donné.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des tâches",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "[{\"id\":\"abc123\",\"inventory_code\":\"INV001\",\"current_node_id\":\"n2\",\"startedBy\":\"user@example.com\",\"created_at\":{\"seconds\":1700000000}}]"))),
            @ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
    })
    @GetMapping(value = "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getStartedTasks(
            @Parameter(description = "Email de l'utilisateur", required = true, example = "user@example.com")
            @RequestParam String email) {
        try {
            return ResponseEntity.ok(backendClient.getStartedTasks(email));
        } catch (Exception e) {
            return badGateway(e);
        }
    }

    /**
     * Liste les inventaires accessibles à un utilisateur, avec l'info canStart.
     *
     * <p>canStart indique si l'utilisateur appartient au groupe du premier formulaire
     * du workflow associé à l'inventaire, et peut donc initier une tâche.
     *
     * @param email email de l'utilisateur
     * @return tableau JSON des inventaires (name, inventory_code, workflows, groupes, canStart)
     */
    @Operation(summary = "Lister les inventaires accessibles",
            description = "Retourne les inventaires auxquels l'utilisateur a accès, avec l'info 'canStart' indiquant s'il peut initier une tâche.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des inventaires",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "[{\"inventory_code\":\"INV001\",\"name\":\"Hôpital Nord\",\"workflows\":[\"wf_abc123\"],\"groupes\":{\"medecins\":[\"user@example.com\"]},\"canStart\":true}]"))),
            @ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
    })
    @GetMapping(value = "/inventories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getInventories(
            @Parameter(description = "Email de l'utilisateur", required = true, example = "user@example.com")
            @RequestParam String email) {
        try {
            return ResponseEntity.ok(backendClient.getInventoriesForUser(email));
        } catch (Exception e) {
            return badGateway(e);
        }
    }

    /**
     * Retourne une erreur 502 Bad Gateway quand le backend est injoignable ou répond avec une erreur.
     * Le message est échappé pour éviter les injections dans la réponse JSON.
     */
    private ResponseEntity<String> badGateway(Exception e) {
        String msg = e.getMessage() == null ? "Backend unavailable" : e.getMessage().replace("\"", "'");
        return ResponseEntity.status(502)
                .body("{\"error\":\"Bad Gateway\",\"details\":\"" + msg + "\"}");
    }
}
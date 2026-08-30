package org.olga.orchestrator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.olga.orchestrator.client.WorkflowBackendClient;
import org.olga.orchestrator.dto.WorkflowSaveRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Endpoints de manipulation des workflows. Toutes les opérations sont déléguées
 *  au backend Olga via {@link WorkflowBackendClient}. */
@RestController
@CrossOrigin
@RequestMapping("/workflows")
@Tag(name = "Workflows", description = "Édition des workflows (liste, détail, sauvegarde, versions).")
public class WorkflowEditorController {

	private final WorkflowBackendClient backendClient;

	public WorkflowEditorController(WorkflowBackendClient backendClient) {
		this.backendClient = backendClient;
	}

	/** GET /workflows → retourne la liste des workflows (sans le détail). */
	@Operation(summary = "Lister les workflows",
			description = "Retourne les métadonnées de tous les workflows (label, id, from_web, last_updated), sans le détail des nœuds.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Liste des workflows",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(value = "[{\"workflow_id\":\"wf_abc123\",\"workflow_label\":\"Mon Workflow\",\"from_web\":true,\"last_updated\":\"...\"},{\"workflow_id\":\"wf_def456\",\"workflow_label\":\"Autre Workflow\",\"from_web\":false,\"last_updated\":\"...\"}]"))),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(value = "{\"error\":\"Bad Gateway\",\"details\":\"Backend unavailable\"}")))
	})
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getWorkflows() {
		try {
			return ResponseEntity.ok(backendClient.getWorkflows());
		} catch (Exception e) {
			return badGateway(e);
		}
	}

	/** GET /workflows/{id} → charge un workflow complet (nodes + edges). */
	@Operation(summary = "Charger un workflow",
			description = "Charge un workflow complet à partir de son id : nœuds (start/end/form/save), arêtes et positions.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Workflow complet",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(value = "{\"workflow_id\":\"wf_abc123\",\"workflow_label\":\"Mon Workflow\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"position\":{\"x\":100,\"y\":100},\"data\":{}},{\"id\":\"n2\",\"type\":\"form\",\"position\":{\"x\":300,\"y\":100},\"data\":{\"form_id\":\"f1\",\"form_label\":\"Demande\",\"form_groups\":[\"group1\"]}}],\"edges\":[{\"id\":\"e1\",\"source\":\"n1\",\"target\":\"n2\",\"animated\":true}]}"))),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@GetMapping(value = "/{workflowId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getWorkflow(
			@Parameter(description = "Identifiant du workflow", required = true, example = "wf_abc123")
			@PathVariable String workflowId) {
		try {
			return ResponseEntity.ok(backendClient.getWorkflow(workflowId));
		} catch (Exception e) {
			return badGateway(e);
		}
	}

	/** PUT /workflows/{id} → sauvegarde le workflow.
	 *  Le backend crée une version avant d'écraser les données.
	 *  Le corps doit contenir un champ "workflow" non-null. */
	@Operation(summary = "Sauvegarder un workflow",
			description = "Sauvegarde le workflow. Le backend crée automatiquement une version avant d'écraser. Le champ 'workflow' est obligatoire.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Workflow sauvegardé",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(value = "{\"document_id\":\"doc_xxx\",\"workflow_id\":\"wf_abc123\"}"))),
			@ApiResponse(responseCode = "400", description = "Le champ 'workflow' est absent",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(value = "{\"error\":\"workflow is required\"}"))),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@PutMapping(
			value = "/{workflowId}",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> saveWorkflow(
			@Parameter(description = "Identifiant du workflow", required = true, example = "wf_abc123")
			@PathVariable String workflowId,
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					description = "Workflow à sauvegarder",
					required = true,
					content = @Content(examples = @ExampleObject(
							value = "{\"edited_by\":\"editor\",\"workflow\":{\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"position\":{\"x\":100,\"y\":100},\"data\":{}}],\"edges\":[]}}")))
			@RequestBody WorkflowSaveRequest request) {
		try {
			if (request.workflow() == null || request.workflow().isNull()) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
						.body("{\"error\":\"workflow is required\"}");
			}

			return ResponseEntity.ok(backendClient.saveWorkflow(workflowId, request));
		} catch (Exception e) {
			return badGateway(e);
		}
	}

	/** GET /workflows/{id}/versions → liste les versions disponibles. */
	@Operation(summary = "Lister les versions d'un workflow",
			description = "Retourne l'historique des versions d'un workflow (horodatage, auteur, action).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Liste des versions",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@GetMapping(value = "/{workflowId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getVersions(
			@Parameter(description = "Identifiant du workflow", required = true, example = "wf_abc123")
			@PathVariable String workflowId) {
		try {
			return ResponseEntity.ok(backendClient.getVersions(workflowId));
		} catch (Exception e) {
			return badGateway(e);
		}
	}

	/** POST /workflows/{id}/versions/{versionId}/restore → restaure une version. */
	@Operation(summary = "Restaurer une version",
			description = "Restaure une version précédente d'un workflow. Le corps est optionnel (peut contenir 'edited_by').")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Version restaurée",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(value = "{\"document_id\":\"doc_xxx\",\"workflow_id\":\"wf_abc123\",\"restored_version_id\":\"v_1\"}"))),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@PostMapping(
			value = "/{workflowId}/versions/{versionId}/restore",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> restoreVersion(
			@Parameter(description = "Identifiant du workflow", required = true, example = "wf_abc123")
			@PathVariable String workflowId,
			@Parameter(description = "Identifiant de la version à restaurer", required = true, example = "v_1")
			@PathVariable String versionId,
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					description = "Corps optionnel pouvant contenir 'edited_by'",
					required = false,
					content = @Content(examples = @ExampleObject(value = "{\"edited_by\":\"editor\"}")))
			@RequestBody(required = false) String requestBody) {
		try {
			return ResponseEntity.ok(backendClient.restoreVersion(workflowId, versionId, requestBody));
		} catch (Exception e) {
			return badGateway(e);
		}
	}

	/** Construit une réponse 502 Bad Gateway avec le message d'erreur du backend. */
	private ResponseEntity<String> badGateway(Exception e) {
		String message = e.getMessage() == null ? "Backend unavailable" : e.getMessage().replace("\"", "'");
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body("{\"error\":\"Bad Gateway\",\"details\":\"" + message + "\"}");
	}
}

package org.olga.orchestrator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.olga.orchestrator.client.WorkflowBackendClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** CRUD des formulaires. Proxifie les appels vers le backend Olga.
 *  Utilisé par l'éditeur visuel pour associer un formulaire à un nœud. */
@RestController
@CrossOrigin
@RequestMapping("/forms")
@Tag(name = "Formulaires", description = "Gestion des formulaires (CRUD) utilisés dans les workflows.")
public class FormEditorController {

	private final WorkflowBackendClient backendClient;

	public FormEditorController(WorkflowBackendClient backendClient) {
		this.backendClient = backendClient;
	}

	/** GET /forms → liste minimale (id, label, groupes). */
	@Operation(summary = "Lister les formulaires",
			description = "Retourne une liste minimaliste des formulaires (id, label, groupes), utilisée par l'éditeur visuel.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Liste des formulaires",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(value = "[{\"form_id\":\"Form1\",\"form_label\":\"Consultation\",\"form_groups\":[\"medecins\"]}]"))),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getForms() {
		try {
			return ResponseEntity.ok(backendClient.getForms());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"error\":\"" + e.getMessage() + "\"}");
		}
	}

	/** GET /forms/{id} → détail complet d'un formulaire. */
	@Operation(summary = "Charger un formulaire",
			description = "Retourne le détail complet d'un formulaire (champs, règles).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Détail du formulaire",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@GetMapping(value = "/{formId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getForm(
			@Parameter(description = "Identifiant du formulaire", required = true, example = "Form1")
			@PathVariable String formId) {
		try {
			return ResponseEntity.ok(backendClient.getForm(formId));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"error\":\"" + e.getMessage() + "\"}");
		}
	}

	/** POST /forms/save → crée un nouveau formulaire. */
	@Operation(summary = "Créer un formulaire",
			description = "Crée un nouveau formulaire à partir du JSON complet du formulaire envoyé dans le corps.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Formulaire créé",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> saveForm(
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					description = "JSON complet du formulaire",
					required = true,
					content = @Content(examples = @ExampleObject(value = "{\"form_id\":\"Form1\",\"form_label\":\"Consultation\",\"form\":[{\"field_key\":\"lastname1\",\"field_label\":\"Nom\",\"field_type\":\"input:text\"}]}")))
			@RequestBody String body) {
		try {
			return ResponseEntity.ok(backendClient.saveForm(body));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"error\":\"" + e.getMessage() + "\"}");
		}
	}

	/** POST /forms/update/{id} → met à jour un formulaire existant. */
	@Operation(summary = "Mettre à jour un formulaire",
			description = "Met à jour un formulaire existant à partir du JSON complet envoyé dans le corps.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Formulaire mis à jour",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@PostMapping(value = "/update/{formId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> updateForm(
			@Parameter(description = "Identifiant du formulaire", required = true, example = "Form1")
			@PathVariable String formId,
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					description = "JSON complet du formulaire",
					required = true,
					content = @Content(examples = @ExampleObject(value = "{\"form_id\":\"Form1\",\"form_label\":\"Consultation\",\"form\":[]}")))
			@RequestBody String body) {
		try {
			return ResponseEntity.ok(backendClient.updateForm(formId, body));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"error\":\"" + e.getMessage() + "\"}");
		}
	}

	/** DELETE /forms/{id} → supprime un formulaire. */
	@Operation(summary = "Supprimer un formulaire",
			description = "Supprime un formulaire existant par son identifiant.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Formulaire supprimé",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
			@ApiResponse(responseCode = "502", description = "Backend injoignable ou erreur")
	})
	@DeleteMapping(value = "/{formId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> deleteForm(
			@Parameter(description = "Identifiant du formulaire", required = true, example = "Form1")
			@PathVariable String formId) {
		try {
			return ResponseEntity.ok(backendClient.deleteForm(formId));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"error\":\"" + e.getMessage() + "\"}");
		}
	}
}

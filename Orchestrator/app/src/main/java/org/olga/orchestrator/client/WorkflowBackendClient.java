package org.olga.orchestrator.client;

import org.olga.orchestrator.dto.WorkflowSaveRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/** Client HTTP qui transforme chaque appel de l'orchestrateur en requête vers le backend Olga.
 *  Agit comme un proxy — les contrôleurs délèguent tout le travail à ce service. */
@Service
public class WorkflowBackendClient {

	private final WebClient webClient;

	public WorkflowBackendClient(@Value("${olga.backend.base-url}") String baseUrl) {
		this.webClient = WebClient.builder()
				.baseUrl(baseUrl)
				.build();
	}

	// ─── Workflows ───────────────────────────────────────

	/** Récupère la liste de tous les workflows (label + meta, sans le détail). */
	public String getWorkflows() {
		return webClient.get()
				.uri("/external/workflows")
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/** Charge un workflow complet (nœuds, liens, positions). */
	public String getWorkflow(String workflowId) {
		return webClient.get()
				.uri("/external/workflows/{workflowId}", workflowId)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/** Sauvegarde un workflow modifié. Le backend crée automatiquement une version avant d'écraser. */
	public String saveWorkflow(String workflowId, WorkflowSaveRequest request) {
		return webClient.put()
				.uri("/external/workflows/{workflowId}", workflowId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/** Liste les versions d'un workflow (horodatage, auteur, action). */
	public String getVersions(String workflowId) {
		return webClient.get()
				.uri("/external/workflows/{workflowId}/versions", workflowId)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/** Restaure une version précédente d'un workflow. */
	public String restoreVersion(String workflowId, String versionId, String requestBody) {
		String body = requestBody == null || requestBody.isBlank() ? "{}" : requestBody;
		return webClient.post()
				.uri("/external/workflows/{workflowId}/versions/{versionId}/restore", workflowId, versionId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	// ─── Execution ──────────────────────────────────────

	/**
	 * Démarre une tâche d'exécution sur un inventaire.
	 * Le backend crée un document task + lookup du 1er formulaire du workflow.
	 * @return { task_id, form } avec la structure complète du formulaire
	 */
	public String startTask(String inventoryId, String email) {
		return webClient.get()
				.uri("/startTask?inventory_id={inventoryId}&email={email}", inventoryId, email)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/**
	 * Soumet les données du formulaire courant et avance dans le workflow.
	 * Le body doit être un objet JSON { field_key: value, ... }.
	 * Le backend fusionne ces données dans task.data, traverse le graphe
	 * (edges), exécute les nœuds "save" s'il y en a, et retourne le prochain
	 * formulaire (ou form: null si fin du workflow).
	 * @return { form } ou { form: null, next_groups } si accès refusé
	 */
	public String nextTask(String taskId, String email, String body) {
		return webClient.post()
				.uri("/next?task_id={taskId}&email={email}", taskId, email)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/**
	 * Interroge l'état actuel d'une tâche (formulaire en cours ou null si terminée).
	 * Utile pour reprendre une tâche après rechargement.
	 * @return { form, data } avec les données déjà collectées
	 */
	public String getTaskStatus(String taskId) {
		return webClient.get()
				.uri("/status?task_id={taskId}", taskId)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/**
	 * Liste les tâches démarrées par un utilisateur.
	 * @return tableau [{ id, inventory_code, current_node_id, startedBy, created_at }]
	 */
	public String getStartedTasks(String email) {
		return webClient.get()
				.uri("/getStartedTasks?email={email}", email)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/**
	 * Liste les inventaires accessibles à un utilisateur.
	 * canStart est calculé côté backend en vérifiant que l'utilisateur
	 * appartient au groupe du premier formulaire du workflow.
	 * @return tableau [{ inventory_code, name, workflows, groupes, canStart }]
	 */
	public String getInventoriesForUser(String email) {
		return webClient.get()
				.uri("/getAllInventoriesForUser?email={email}", email)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	// ─── Forms ───────────────────────────────────────────

	/** Récupère la liste minimaliste des formulaires (id, label, groupes). */
	public String getForms() {
		return webClient.get()
				.uri("/forms/getMinimal")
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/** Récupère le détail complet d'un formulaire (champs, règles). */
	public String getForm(String formId) {
		return webClient.get()
				.uri("/forms/getFromID/{formId}", formId)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/** Crée un nouveau formulaire. */
	public String saveForm(String body) {
		return webClient.post()
				.uri("/forms/save")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/** Met à jour un formulaire existant. */
	public String updateForm(String formId, String body) {
		return webClient.post()
				.uri("/forms/update/{form_id}", formId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/** Supprime un formulaire. */
	public String deleteForm(String formId) {
		return webClient.delete()
				.uri("/forms/delete/{form_id}", formId)
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}
}

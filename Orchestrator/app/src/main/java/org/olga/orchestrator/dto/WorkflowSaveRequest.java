package org.olga.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** Corps attendu par PUT /workflows/{id}.
 *  @param editedBy  Nom ou identifiant de l'éditeur (traçabilité dans les versions)
 *  @param workflow  Arbre JSON complet du workflow (nodes + edges) */
public record WorkflowSaveRequest(
		@JsonProperty("edited_by") String editedBy,
		JsonNode workflow
) {
}

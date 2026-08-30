package org.olga.orchestrator.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Endpoint de liveness simple, utilisé par le healthcheck Docker.
 *  L'orchestrateur est vivant dès que Spring est démarré. */
@RestController
public class HealthController {

	@GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, String>> health() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("status", "UP");
		return ResponseEntity.ok(body);
	}
}

package org.olga.orchestrator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Ouvre le CORS pour toutes les origines — nécessaire car l'éditeur visuel
 *  est servi depuis un domaine différent (ex. localhost:8080) et communique
 *  avec l'orchestrateur via fetch/JavaScript. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOriginPatterns("*")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.maxAge(3600);
	}

}

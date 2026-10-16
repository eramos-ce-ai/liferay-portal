package com.liferay.useraudit.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows cross-origin requests from the configured Liferay DXP domains.
 */
@Configuration
public class LiferayWebMvcConfiguration implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry corsRegistry) {
		List<String> allowedOrigins = new ArrayList<>();

		for (String lxcDXPDomain : _lxcDXPDomains.split("\\s*[,\n]\\s*")) {
			allowedOrigins.add("http://" + lxcDXPDomain);
			allowedOrigins.add("https://" + lxcDXPDomain);
		}

		corsRegistry.addMapping(
			"/**"
		).allowedHeaders(
			"Authorization", "Content-Type"
		).allowedMethods(
			"DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"
		).allowedOrigins(
			allowedOrigins.toArray(new String[0])
		);
	}

	@Value("${com.liferay.lxc.dxp.domains}")
	private String _lxcDXPDomains;

}

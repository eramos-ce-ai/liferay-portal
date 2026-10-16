package com.liferay.useraudit.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URI;
import java.net.URL;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures the Spring Security 7 resource server that validates the
 * Liferay-issued JWT presented by the inbound Object Action request.
 */
@Configuration
@EnableWebSecurity
public class LiferaySecurityConfiguration {

	public LiferaySecurityConfiguration(Environment environment) {
		_environment = environment;
	}

	@Bean
	public JwtDecoder jwtDecoder() throws Exception {
		String externalReferenceCodes = _environment.getProperty(
			"liferay.oauth.application.external.reference.codes");

		if (externalReferenceCodes == null) {
			throw new IllegalArgumentException(
				"Property \"liferay.oauth.application.external.reference." +
					"codes\" is not defined");
		}

		DefaultJWTProcessor<SecurityContext> defaultJWTProcessor =
			new DefaultJWTProcessor<>();

		URI jwkSetURI = URI.create(
			_lxcDXPServerProtocol + "://" + _lxcDXPMainDomain +
				"/o/oauth2/jwks");

		URL jwkSetURL = jwkSetURI.toURL();

		defaultJWTProcessor.setJWSKeySelector(
			JWSAlgorithmFamilyJWSKeySelector.fromJWKSetURL(jwkSetURL));

		defaultJWTProcessor.setJWSTypeVerifier(
			new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt")));

		NimbusJwtDecoder nimbusJwtDecoder = new NimbusJwtDecoder(
			defaultJWTProcessor);

		Map<String, String> clientIds = new HashMap<>();

		for (String externalReferenceCode : externalReferenceCodes.split(",")) {
			externalReferenceCode = externalReferenceCode.trim();

			String clientId = _environment.getProperty(
				externalReferenceCode + ".oauth2.user.agent.client.id");

			if (clientId != null) {
				clientIds.put(externalReferenceCode, clientId);
			}
		}

		nimbusJwtDecoder.setJwtValidator(
			new ClientIdOAuth2TokenValidator(clientIds));

		return nimbusJwtDecoder;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity)
		throws Exception {

		httpSecurity.cors(
			Customizer.withDefaults()
		).csrf(
			csrf -> csrf.disable()
		).sessionManagement(
			sessionManagement -> sessionManagement.sessionCreationPolicy(
				SessionCreationPolicy.STATELESS)
		).authorizeHttpRequests(
			authorize -> authorize.requestMatchers(
				_liferayOAuthURLsExcludes
			).permitAll(
			).anyRequest(
			).authenticated()
		).oauth2ResourceServer(
			oauth2ResourceServer -> oauth2ResourceServer.jwt(
				Customizer.withDefaults())
		);

		return httpSecurity.build();
	}

	private final Environment _environment;

	@Value("${liferay.oauth.urls.excludes:}")
	private String[] _liferayOAuthURLsExcludes;

	@Value("${com.liferay.lxc.dxp.mainDomain}")
	private String _lxcDXPMainDomain;

	@Value("${com.liferay.lxc.dxp.server.protocol}")
	private String _lxcDXPServerProtocol;

	private static class ClientIdOAuth2TokenValidator
		implements OAuth2TokenValidator<Jwt> {

		public ClientIdOAuth2TokenValidator(Map<String, String> clientIds) {
			_clientIds = clientIds;
		}

		@Override
		public OAuth2TokenValidatorResult validate(Jwt jwt) {
			String clientId = jwt.getClaimAsString("client_id");

			if (_clientIds.containsValue(clientId)) {
				return OAuth2TokenValidatorResult.success();
			}

			return OAuth2TokenValidatorResult.failure(
				new OAuth2Error(
					"invalid_token", "The client_id does not match", null));
		}

		private final Map<String, String> _clientIds;

	}

}

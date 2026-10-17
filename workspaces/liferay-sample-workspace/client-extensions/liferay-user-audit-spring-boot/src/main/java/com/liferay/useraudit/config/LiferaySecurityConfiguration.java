package com.liferay.useraudit.config;

import com.liferay.useraudit.security.ClientIdOAuth2TokenValidator;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URI;
import java.net.URL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
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

	/**
	 * Builds the composite JWT validator. In addition to the default
	 * timestamp checks and the Liferay {@code client_id} check, issuer
	 * validation is enforced (so a token with an invalid {@code iss} claim
	 * is rejected) unless it is explicitly disabled for local development.
	 */
	public static OAuth2TokenValidator<Jwt> buildJwtValidator(
		Map<String, String> clientIds, String issuer,
		boolean issuerValidationEnabled) {

		List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();

		validators.add(new JwtTimestampValidator());

		if (issuerValidationEnabled) {
			if ((issuer == null) || issuer.isBlank()) {
				throw new IllegalStateException(
					"Issuer validation is enabled but no issuer is " +
						"configured. Set \"liferay.oauth.jwt.issuer\" or " +
							"disable validation for local development with " +
								"\"liferay.oauth.jwt.issuer.validation." +
									"enabled=false\".");
			}

			validators.add(new JwtIssuerValidator(issuer));
		}

		validators.add(new ClientIdOAuth2TokenValidator(clientIds));

		return new DelegatingOAuth2TokenValidator<>(validators);
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

		String issuer = _jwtIssuer;

		if ((issuer == null) || issuer.isBlank()) {
			issuer = _lxcDXPServerProtocol + "://" + _lxcDXPMainDomain;
		}

		if (_log.isInfoEnabled()) {
			_log.info(
				"Configuring JWT validation (issuer validation enabled: {}, " +
					"issuer: {})",
				_jwtIssuerValidationEnabled, issuer);
		}

		nimbusJwtDecoder.setJwtValidator(
			buildJwtValidator(clientIds, issuer, _jwtIssuerValidationEnabled));

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

	private static final Logger _log = LoggerFactory.getLogger(
		LiferaySecurityConfiguration.class);

	private final Environment _environment;

	@Value("${liferay.oauth.jwt.issuer:}")
	private String _jwtIssuer;

	@Value("${liferay.oauth.jwt.issuer.validation.enabled:true}")
	private boolean _jwtIssuerValidationEnabled;

	@Value("${liferay.oauth.urls.excludes:}")
	private String[] _liferayOAuthURLsExcludes;

	@Value("${com.liferay.lxc.dxp.mainDomain}")
	private String _lxcDXPMainDomain;

	@Value("${com.liferay.lxc.dxp.server.protocol}")
	private String _lxcDXPServerProtocol;

}

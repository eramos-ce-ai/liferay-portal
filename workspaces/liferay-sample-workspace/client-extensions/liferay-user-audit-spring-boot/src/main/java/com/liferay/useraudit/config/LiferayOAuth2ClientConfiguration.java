package com.liferay.useraudit.config;

import com.liferay.useraudit.oauth.LiferayOAuth2Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Builds client-credentials OAuth2 client registrations so the client
 * extension can obtain its own headless-server access token to call the
 * Liferay Headless Admin User API.
 */
@Configuration
public class LiferayOAuth2ClientConfiguration {

	public LiferayOAuth2ClientConfiguration(Environment environment) {
		_environment = environment;
	}

	@Bean
	public AuthorizedClientServiceOAuth2AuthorizedClientManager
		authorizedClientServiceOAuth2AuthorizedClientManager(
			ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {

		AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
			new AuthorizedClientServiceOAuth2AuthorizedClientManager(
				clientRegistrationRepository, oAuth2AuthorizedClientService);

		manager.setAuthorizedClientProvider(
			OAuth2AuthorizedClientProviderBuilder.builder(
			).clientCredentials(
			).build());

		return manager;
	}

	@Bean
	public ClientRegistrationRepository clientRegistrationRepository() {
		List<ClientRegistration> clientRegistrations =
			_getClientRegistrations();

		if (clientRegistrations.isEmpty()) {
			return new InMemoryClientRegistrationRepository(
				Collections.emptyMap());
		}

		return new InMemoryClientRegistrationRepository(clientRegistrations);
	}

	@Bean
	public OAuth2AuthorizedClientService oAuth2AuthorizedClientService(
		ClientRegistrationRepository clientRegistrationRepository) {

		return new InMemoryOAuth2AuthorizedClientService(
			clientRegistrationRepository);
	}

	private List<ClientRegistration> _getClientRegistrations() {
		String externalReferenceCodes = _environment.getProperty(
			"liferay.oauth.application.external.reference.codes");

		if (externalReferenceCodes == null) {
			throw new IllegalArgumentException(
				"Property \"liferay.oauth.application.external.reference." +
					"codes\" is not defined");
		}

		List<ClientRegistration> clientRegistrations = new ArrayList<>();

		for (String externalReferenceCode : externalReferenceCodes.split(",")) {
			externalReferenceCode = externalReferenceCode.trim();

			String clientId = _environment.getProperty(
				externalReferenceCode + ".oauth2.headless.server.client.id");

			if (clientId == null) {
				clientId = LiferayOAuth2Util.getClientId(
					externalReferenceCode, _lxcDXPMainDomain,
					_lxcDXPServerProtocol);
			}

			String clientSecret = _environment.getProperty(
				externalReferenceCode +
					".oauth2.headless.server.client.secret");

			if ((clientId == null) || (clientSecret == null)) {
				if (_log.isInfoEnabled()) {
					_log.info(
						"Skipping external reference code {}, no headless " +
							"server credentials were found",
						externalReferenceCode);
				}

				continue;
			}

			String tokenURI = _environment.getProperty(
				externalReferenceCode + ".oauth2.token.uri", "/o/oauth2/token");

			if (!tokenURI.contains("://")) {
				tokenURI =
					_lxcDXPServerProtocol + "://" + _lxcDXPMainDomain + tokenURI;
			}

			clientRegistrations.add(
				ClientRegistration.withRegistrationId(
					externalReferenceCode
				).tokenUri(
					tokenURI
				).clientId(
					clientId
				).clientSecret(
					clientSecret
				).authorizationGrantType(
					AuthorizationGrantType.CLIENT_CREDENTIALS
				).clientAuthenticationMethod(
					ClientAuthenticationMethod.CLIENT_SECRET_POST
				).build());
		}

		return clientRegistrations;
	}

	private static final Logger _log = LoggerFactory.getLogger(
		LiferayOAuth2ClientConfiguration.class);

	private final Environment _environment;

	@Value("${com.liferay.lxc.dxp.mainDomain}")
	private String _lxcDXPMainDomain;

	@Value("${com.liferay.lxc.dxp.server.protocol}")
	private String _lxcDXPServerProtocol;

}

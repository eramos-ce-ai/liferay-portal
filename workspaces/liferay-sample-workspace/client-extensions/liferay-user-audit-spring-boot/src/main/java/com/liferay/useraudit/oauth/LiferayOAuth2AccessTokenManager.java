package com.liferay.useraudit.oauth;

import java.time.Instant;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

/**
 * Obtains and caches client-credentials access tokens, refreshing them
 * shortly before they expire.
 */
@Component
public class LiferayOAuth2AccessTokenManager {

	public LiferayOAuth2AccessTokenManager(
		AuthorizedClientServiceOAuth2AuthorizedClientManager
			authorizedClientServiceOAuth2AuthorizedClientManager) {

		_authorizedClientServiceOAuth2AuthorizedClientManager =
			authorizedClientServiceOAuth2AuthorizedClientManager;
	}

	public String getAuthorization(String externalReferenceCode) {
		OAuth2AccessToken oAuth2AccessToken = _getOAuth2AccessToken(
			externalReferenceCode);

		if (oAuth2AccessToken == null) {
			return null;
		}

		OAuth2AccessToken.TokenType tokenType =
			oAuth2AccessToken.getTokenType();

		return tokenType.getValue() + " " + oAuth2AccessToken.getTokenValue();
	}

	private OAuth2AccessToken _authorize(String externalReferenceCode) {
		OAuth2AuthorizeRequest oAuth2AuthorizeRequest =
			OAuth2AuthorizeRequest.withClientRegistrationId(
				externalReferenceCode
			).principal(
				externalReferenceCode
			).build();

		OAuth2AuthorizedClient oAuth2AuthorizedClient =
			_authorizedClientServiceOAuth2AuthorizedClientManager.authorize(
				oAuth2AuthorizeRequest);

		if (oAuth2AuthorizedClient == null) {
			_log.error(
				"Unable to authorize client for external reference code {}",
				externalReferenceCode);

			return null;
		}

		return oAuth2AuthorizedClient.getAccessToken();
	}

	private OAuth2AccessToken _getOAuth2AccessToken(
		String externalReferenceCode) {

		synchronized (_oAuth2AccessTokens) {
			OAuth2AccessToken oAuth2AccessToken = _oAuth2AccessTokens.get(
				externalReferenceCode);

			Instant now = Instant.now();

			if ((oAuth2AccessToken != null) &&
				(oAuth2AccessToken.getExpiresAt() != null) &&
				oAuth2AccessToken.getExpiresAt(
				).isAfter(
					now.plusSeconds(60)
				)) {

				return oAuth2AccessToken;
			}

			oAuth2AccessToken = _authorize(externalReferenceCode);

			if (oAuth2AccessToken != null) {
				_oAuth2AccessTokens.put(
					externalReferenceCode, oAuth2AccessToken);
			}

			return oAuth2AccessToken;
		}
	}

	private static final Logger _log = LoggerFactory.getLogger(
		LiferayOAuth2AccessTokenManager.class);

	private final AuthorizedClientServiceOAuth2AuthorizedClientManager
		_authorizedClientServiceOAuth2AuthorizedClientManager;

	private final Map<String, OAuth2AccessToken> _oAuth2AccessTokens =
		new HashMap<>();

}

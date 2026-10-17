package com.liferay.useraudit.security;

import java.util.Map;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Accepts a JWT only when its {@code client_id} claim matches one of the
 * configured Liferay OAuth2 user-agent application client IDs.
 */
public class ClientIdOAuth2TokenValidator
	implements OAuth2TokenValidator<Jwt> {

	public ClientIdOAuth2TokenValidator(Map<String, String> clientIds) {
		_clientIds = clientIds;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		String clientId = jwt.getClaimAsString("client_id");

		if ((clientId != null) && _clientIds.containsValue(clientId)) {
			return OAuth2TokenValidatorResult.success();
		}

		return OAuth2TokenValidatorResult.failure(
			new OAuth2Error(
				"invalid_token",
				"The client_id claim does not match a configured OAuth2 " +
					"application",
				null));
	}

	private final Map<String, String> _clientIds;

}

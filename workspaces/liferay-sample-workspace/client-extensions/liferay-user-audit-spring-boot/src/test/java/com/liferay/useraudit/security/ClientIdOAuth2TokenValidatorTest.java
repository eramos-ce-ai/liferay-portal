package com.liferay.useraudit.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class ClientIdOAuth2TokenValidatorTest {

	@Test
	public void testMatchingClientId() {
		ClientIdOAuth2TokenValidator clientIdOAuth2TokenValidator =
			new ClientIdOAuth2TokenValidator(
				Collections.singletonMap("erc", "client-123"));

		OAuth2TokenValidatorResult oAuth2TokenValidatorResult =
			clientIdOAuth2TokenValidator.validate(_jwt("client-123"));

		assertFalse(oAuth2TokenValidatorResult.hasErrors());
	}

	@Test
	public void testMissingClientId() {
		ClientIdOAuth2TokenValidator clientIdOAuth2TokenValidator =
			new ClientIdOAuth2TokenValidator(
				Collections.singletonMap("erc", "client-123"));

		Jwt jwt = Jwt.withTokenValue(
			"t"
		).header(
			"alg", "none"
		).subject(
			"subject"
		).build();

		OAuth2TokenValidatorResult oAuth2TokenValidatorResult =
			clientIdOAuth2TokenValidator.validate(jwt);

		assertTrue(oAuth2TokenValidatorResult.hasErrors());
	}

	@Test
	public void testNonMatchingClientId() {
		ClientIdOAuth2TokenValidator clientIdOAuth2TokenValidator =
			new ClientIdOAuth2TokenValidator(
				Collections.singletonMap("erc", "client-123"));

		OAuth2TokenValidatorResult oAuth2TokenValidatorResult =
			clientIdOAuth2TokenValidator.validate(_jwt("someone-else"));

		assertTrue(oAuth2TokenValidatorResult.hasErrors());
	}

	private Jwt _jwt(String clientId) {
		return Jwt.withTokenValue(
			"t"
		).header(
			"alg", "none"
		).claim(
			"client_id", clientId
		).build();
	}

}

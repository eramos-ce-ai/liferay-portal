package com.liferay.useraudit.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class LiferaySecurityConfigurationTest {

	@Test
	public void testBadClientIdStillRejectedWhenIssuerValid() {
		OAuth2TokenValidator<Jwt> oAuth2TokenValidator =
			LiferaySecurityConfiguration.buildJwtValidator(
				_CLIENT_IDS, _ISSUER, true);

		OAuth2TokenValidatorResult oAuth2TokenValidatorResult =
			oAuth2TokenValidator.validate(_jwt("not-registered", _ISSUER));

		assertTrue(oAuth2TokenValidatorResult.hasErrors());
	}

	@Test
	public void testEnabledWithNoIssuerThrows() {
		assertThrows(
			IllegalStateException.class,
			() -> LiferaySecurityConfiguration.buildJwtValidator(
				_CLIENT_IDS, "", true));
	}

	@Test
	public void testInvalidIssuerAllowedWhenDisabled() {
		OAuth2TokenValidator<Jwt> oAuth2TokenValidator =
			LiferaySecurityConfiguration.buildJwtValidator(
				_CLIENT_IDS, _ISSUER, false);

		OAuth2TokenValidatorResult oAuth2TokenValidatorResult =
			oAuth2TokenValidator.validate(
				_jwt("client-123", "https://evil.example.com"));

		assertFalse(oAuth2TokenValidatorResult.hasErrors());
	}

	@Test
	public void testInvalidIssuerRejectedWhenEnabled() {
		OAuth2TokenValidator<Jwt> oAuth2TokenValidator =
			LiferaySecurityConfiguration.buildJwtValidator(
				_CLIENT_IDS, _ISSUER, true);

		OAuth2TokenValidatorResult oAuth2TokenValidatorResult =
			oAuth2TokenValidator.validate(
				_jwt("client-123", "https://evil.example.com"));

		assertTrue(oAuth2TokenValidatorResult.hasErrors());
	}

	@Test
	public void testValidIssuerAcceptedWhenEnabled() {
		OAuth2TokenValidator<Jwt> oAuth2TokenValidator =
			LiferaySecurityConfiguration.buildJwtValidator(
				_CLIENT_IDS, _ISSUER, true);

		OAuth2TokenValidatorResult oAuth2TokenValidatorResult =
			oAuth2TokenValidator.validate(_jwt("client-123", _ISSUER));

		assertFalse(oAuth2TokenValidatorResult.hasErrors());
	}

	private Jwt _jwt(String clientId, String issuer) {
		return Jwt.withTokenValue(
			"t"
		).header(
			"alg", "none"
		).claim(
			"client_id", clientId
		).issuer(
			issuer
		).issuedAt(
			Instant.now()
		).expiresAt(
			Instant.now().plusSeconds(300)
		).build();
	}

	private static final Map<String, String> _CLIENT_IDS =
		Collections.singletonMap("erc", "client-123");

	private static final String _ISSUER = "https://liferay.example.com";

}

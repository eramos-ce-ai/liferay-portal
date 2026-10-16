package com.liferay.useraudit.service;

import com.liferay.useraudit.oauth.LiferayOAuth2AccessTokenManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Thin client over the Liferay Headless Admin User API. Outbound calls are
 * authorized with the client extension's own headless-server access token.
 */
@Service
public class HeadlessAdminUserClient {

	public HeadlessAdminUserClient(
		LiferayOAuth2AccessTokenManager liferayOAuth2AccessTokenManager) {

		_liferayOAuth2AccessTokenManager = liferayOAuth2AccessTokenManager;
	}

	public String getUserAccount(
		String externalReferenceCode, long userAccountId) {

		String authorization =
			_liferayOAuth2AccessTokenManager.getAuthorization(
				externalReferenceCode);

		if (authorization == null) {
			throw new IllegalStateException(
				"Unable to obtain an access token for external reference " +
					"code " + externalReferenceCode);
		}

		String url =
			_lxcDXPServerProtocol + "://" + _lxcDXPMainDomain +
				"/o/headless-admin-user/v1.0/user-accounts/" + userAccountId;

		RestClient restClient = RestClient.create();

		return restClient.get(
		).uri(
			url
		).header(
			HttpHeaders.AUTHORIZATION, authorization
		).accept(
			MediaType.APPLICATION_JSON
		).retrieve(
		).body(
			String.class
		);
	}

	private final LiferayOAuth2AccessTokenManager
		_liferayOAuth2AccessTokenManager;

	@Value("${com.liferay.lxc.dxp.mainDomain}")
	private String _lxcDXPMainDomain;

	@Value("${com.liferay.lxc.dxp.server.protocol}")
	private String _lxcDXPServerProtocol;

}

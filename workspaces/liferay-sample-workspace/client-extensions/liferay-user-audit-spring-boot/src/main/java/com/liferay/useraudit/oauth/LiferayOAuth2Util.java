package com.liferay.useraudit.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.client.RestClient;

/**
 * Resolves the OAuth2 client ID for a given external reference code by
 * querying the Liferay OAuth2 application endpoint.
 */
public class LiferayOAuth2Util {

	public static String getClientId(
		String externalReferenceCode, String lxcDXPMainDomain,
		String lxcDXPServerProtocol) {

		try {
			String url =
				lxcDXPServerProtocol + "://" + lxcDXPMainDomain +
					"/o/oauth2/application?externalReferenceCode={erc}";

			RestClient restClient = RestClient.create();

			ApplicationInfo applicationInfo = restClient.get(
			).uri(
				url, externalReferenceCode
			).retrieve(
			).body(
				ApplicationInfo.class
			);

			if (applicationInfo != null) {
				return applicationInfo.client_id;
			}
		}
		catch (Exception exception) {
			_log.warn("Unable to get client ID: {}", exception.getMessage());
		}

		return null;
	}

	private static final Logger _log = LoggerFactory.getLogger(
		LiferayOAuth2Util.class);

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class ApplicationInfo {

		public String client_id;
		public String homePageURL;

	}

}

package com.liferay.useraudit.web;

import com.liferay.useraudit.service.HeadlessAdminUserClient;

import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles the Liferay Object Action that fires when a custom object entry is
 * updated. It resolves and logs the user who executed the update and the user
 * who originally created the entry, using the Headless Admin User API.
 */
@RequestMapping("/object/action/user-audit")
@RestController
public class ObjectActionUserAuditController {

	public ObjectActionUserAuditController(
		HeadlessAdminUserClient headlessAdminUserClient) {

		_headlessAdminUserClient = headlessAdminUserClient;
	}

	@PostMapping
	public ResponseEntity<String> post(
		@AuthenticationPrincipal Jwt jwt, @RequestBody String json) {

		JSONObject payloadJSONObject = new JSONObject(json);

		long executingUserId = _getExecutingUserId(payloadJSONObject, jwt);

		JSONObject modelDTOJSONObject = _getModelDTOJSONObject(
			payloadJSONObject);

		long creatorUserId = _getCreatorUserId(modelDTOJSONObject);

		String objectActionTriggerKey = payloadJSONObject.optString(
			"objectActionTriggerKey", "onAfterUpdate");

		long objectEntryId = 0;

		if (modelDTOJSONObject != null) {
			objectEntryId = modelDTOJSONObject.optLong("id", 0);
		}

		JSONObject executingUserJSONObject = _fetchUserAccount(executingUserId);

		JSONObject creatorUserJSONObject = null;

		if ((creatorUserId > 0) && (creatorUserId == executingUserId)) {
			creatorUserJSONObject = executingUserJSONObject;
		}
		else if (creatorUserId > 0) {
			creatorUserJSONObject = _fetchUserAccount(creatorUserId);
		}

		_logReport(
			objectActionTriggerKey, objectEntryId, executingUserId,
			executingUserJSONObject, creatorUserId, creatorUserJSONObject);

		return new ResponseEntity<>(json, HttpStatus.OK);
	}

	private JSONObject _fetchUserAccount(long userAccountId) {
		if (userAccountId <= 0) {
			return null;
		}

		try {
			String response = _headlessAdminUserClient.getUserAccount(
				_oAuthApplicationHeadlessServerERC, userAccountId);

			return new JSONObject(response);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to fetch user account " + userAccountId, exception);

			return null;
		}
	}

	private String _formatUserBlock(long userId, JSONObject userJSONObject) {
		if (userJSONObject == null) {
			return String.format(
				"   User ID      : %d%n   (user details unavailable)%n",
				userId);
		}

		String name = userJSONObject.optString("name", "");

		if (name.isEmpty()) {
			String givenName = userJSONObject.optString("givenName", "");
			String familyName = userJSONObject.optString("familyName", "");

			name = (givenName + " " + familyName).trim();
		}

		return String.format(
			"   User ID      : %d%n   Name         : %s%n   Screen Name  : " +
				"%s%n   Email        : %s%n",
			userJSONObject.optLong("id", userId), name,
			userJSONObject.optString("alternateName", ""),
			userJSONObject.optString("emailAddress", ""));
	}

	private long _getCreatorUserId(JSONObject modelDTOJSONObject) {
		if (modelDTOJSONObject == null) {
			return 0;
		}

		JSONObject creatorJSONObject = modelDTOJSONObject.optJSONObject(
			"creator");

		if (creatorJSONObject != null) {
			return creatorJSONObject.optLong("id", 0);
		}

		return modelDTOJSONObject.optLong("creatorId", 0);
	}

	private long _getExecutingUserId(JSONObject payloadJSONObject, Jwt jwt) {
		long userId = payloadJSONObject.optLong("userId", 0);

		if (userId > 0) {
			return userId;
		}

		if (jwt != null) {
			Object userIdClaim = jwt.getClaim("userId");

			if (userIdClaim != null) {
				try {
					return Long.parseLong(String.valueOf(userIdClaim));
				}
				catch (NumberFormatException numberFormatException) {
				}
			}
		}

		return 0;
	}

	private JSONObject _getModelDTOJSONObject(JSONObject payloadJSONObject) {
		for (String key : payloadJSONObject.keySet()) {
			if (key.startsWith("modelDTO")) {
				return payloadJSONObject.optJSONObject(key);
			}
		}

		if (payloadJSONObject.has("objectEntry")) {
			return payloadJSONObject.optJSONObject("objectEntry");
		}

		return payloadJSONObject.optJSONObject("model");
	}

	private void _logReport(
		String objectActionTriggerKey, long objectEntryId,
		long executingUserId, JSONObject executingUserJSONObject,
		long creatorUserId, JSONObject creatorUserJSONObject) {

		String line =
			"==================================================" +
				"======================";
		String divider =
			"--------------------------------------------------" +
				"----------------------";

		String newLine = System.lineSeparator();

		StringBuilder sb = new StringBuilder();

		sb.append(newLine);
		sb.append(line);
		sb.append(newLine);
		sb.append(
			String.format(
				" Object Action Audit  |  Trigger: %s  |  Entry ID: %d",
				objectActionTriggerKey, objectEntryId));
		sb.append(newLine);
		sb.append(line);
		sb.append(newLine);
		sb.append(" Executed By (updated the entry)");
		sb.append(newLine);
		sb.append(_formatUserBlock(executingUserId, executingUserJSONObject));
		sb.append(divider);
		sb.append(newLine);
		sb.append(" Original Creator (created the entry)");
		sb.append(newLine);
		sb.append(_formatUserBlock(creatorUserId, creatorUserJSONObject));
		sb.append(line);

		_log.info(sb.toString());
	}

	private static final Logger _log = LoggerFactory.getLogger(
		ObjectActionUserAuditController.class);

	private final HeadlessAdminUserClient _headlessAdminUserClient;

	@Value("${liferay.user.audit.oauth.headless.server.erc}")
	private String _oAuthApplicationHeadlessServerERC;

}

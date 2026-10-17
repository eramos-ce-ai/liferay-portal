package com.liferay.useraudit.web;

import com.liferay.useraudit.service.HeadlessAdminUserClient;
import com.liferay.useraudit.service.UserAuditProcessor;

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
		HeadlessAdminUserClient headlessAdminUserClient,
		UserAuditProcessor userAuditProcessor) {

		_headlessAdminUserClient = headlessAdminUserClient;
		_userAuditProcessor = userAuditProcessor;
	}

	@PostMapping
	public ResponseEntity<String> post(
		@AuthenticationPrincipal Jwt jwt, @RequestBody String json) {

		JSONObject payloadJSONObject = new JSONObject(json);

		JSONObject modelDTOJSONObject =
			_userAuditProcessor.getModelDTOJSONObject(payloadJSONObject);

		long executingUserId = _userAuditProcessor.getExecutingUserId(
			payloadJSONObject);

		if (executingUserId <= 0) {
			executingUserId = _getUserIdFromJwt(jwt);
		}

		long creatorUserId = _userAuditProcessor.getCreatorUserId(
			modelDTOJSONObject);

		long objectEntryId = _userAuditProcessor.getObjectEntryId(
			modelDTOJSONObject);

		String objectActionTriggerKey = payloadJSONObject.optString(
			"objectActionTriggerKey", "onAfterUpdate");

		JSONObject executingUserJSONObject = _fetchUserAccount(executingUserId);

		JSONObject creatorUserJSONObject = null;

		if ((creatorUserId > 0) && (creatorUserId == executingUserId)) {
			creatorUserJSONObject = executingUserJSONObject;
		}
		else if (creatorUserId > 0) {
			creatorUserJSONObject = _fetchUserAccount(creatorUserId);
		}

		String report = _userAuditProcessor.buildReport(
			objectActionTriggerKey, objectEntryId, executingUserId,
			executingUserJSONObject, creatorUserId, creatorUserJSONObject);

		_log.info(report);

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

	private long _getUserIdFromJwt(Jwt jwt) {
		if (jwt == null) {
			return 0;
		}

		Object userIdClaim = jwt.getClaim("userId");

		if (userIdClaim == null) {
			return 0;
		}

		try {
			return Long.parseLong(String.valueOf(userIdClaim));
		}
		catch (NumberFormatException numberFormatException) {
			return 0;
		}
	}

	private static final Logger _log = LoggerFactory.getLogger(
		ObjectActionUserAuditController.class);

	private final HeadlessAdminUserClient _headlessAdminUserClient;

	@Value("${liferay.user.audit.oauth.headless.server.erc}")
	private String _oAuthApplicationHeadlessServerERC;

	private final UserAuditProcessor _userAuditProcessor;

}

package com.liferay.useraudit.service;

import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * Extracts audit-relevant fields from an Object Action payload and formats
 * the audit report. Deliberately free of Spring Security and HTTP types so
 * the core logic is easy to unit test.
 */
@Component
public class UserAuditProcessor {

	public String buildReport(
		String objectActionTriggerKey, long objectEntryId,
		long executingUserId, JSONObject executingUserJSONObject,
		long creatorUserId, JSONObject creatorUserJSONObject) {

		String line = "=".repeat(74);
		String divider = "-".repeat(74);
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

		return sb.toString();
	}

	public long getCreatorUserId(JSONObject modelDTOJSONObject) {
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

	public long getExecutingUserId(JSONObject payloadJSONObject) {
		return payloadJSONObject.optLong("userId", 0);
	}

	public JSONObject getModelDTOJSONObject(JSONObject payloadJSONObject) {
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

	public long getObjectEntryId(JSONObject modelDTOJSONObject) {
		if (modelDTOJSONObject == null) {
			return 0;
		}

		return modelDTOJSONObject.optLong("id", 0);
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

}

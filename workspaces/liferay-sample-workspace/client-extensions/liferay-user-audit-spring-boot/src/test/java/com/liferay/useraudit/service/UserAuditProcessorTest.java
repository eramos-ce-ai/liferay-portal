package com.liferay.useraudit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;

import org.junit.jupiter.api.Test;

public class UserAuditProcessorTest {

	@Test
	public void testBuildReportContainsBothUsers() {
		JSONObject executingUser = new JSONObject();

		executingUser.put("id", 20125L);
		executingUser.put("name", "Jane Updater");
		executingUser.put("alternateName", "jane");
		executingUser.put("emailAddress", "jane@example.com");

		JSONObject creatorUser = new JSONObject();

		creatorUser.put("id", 20124L);
		creatorUser.put("name", "John Creator");
		creatorUser.put("alternateName", "john");
		creatorUser.put("emailAddress", "john@example.com");

		String report = _userAuditProcessor.buildReport(
			"onAfterUpdate", 43210L, 20125L, executingUser, 20124L,
			creatorUser);

		assertTrue(report.contains("Jane Updater"));
		assertTrue(report.contains("John Creator"));
		assertTrue(report.contains("jane@example.com"));
		assertTrue(report.contains("john@example.com"));
		assertTrue(report.contains("onAfterUpdate"));
		assertTrue(report.contains("43210"));
	}

	@Test
	public void testBuildReportHandlesMissingUser() {
		String report = _userAuditProcessor.buildReport(
			"onAfterUpdate", 1L, 20125L, null, 0L, null);

		assertTrue(report.contains("user details unavailable"));
	}

	@Test
	public void testGetCreatorUserIdFromCreatorIdField() {
		JSONObject modelDTO = new JSONObject();

		modelDTO.put("creatorId", 555L);

		assertEquals(555L, _userAuditProcessor.getCreatorUserId(modelDTO));
	}

	@Test
	public void testGetCreatorUserIdFromCreatorObject() {
		JSONObject creator = new JSONObject();

		creator.put("id", 20124L);

		JSONObject modelDTO = new JSONObject();

		modelDTO.put("creator", creator);

		assertEquals(20124L, _userAuditProcessor.getCreatorUserId(modelDTO));
	}

	@Test
	public void testGetCreatorUserIdNullModel() {
		assertEquals(0L, _userAuditProcessor.getCreatorUserId(null));
	}

	@Test
	public void testGetExecutingUserId() {
		JSONObject payload = new JSONObject();

		payload.put("userId", 20125L);

		assertEquals(20125L, _userAuditProcessor.getExecutingUserId(payload));
	}

	@Test
	public void testGetModelDTOByPrefix() {
		JSONObject modelDTO = new JSONObject();

		modelDTO.put("id", 1L);

		JSONObject payload = new JSONObject();

		payload.put("modelDTOUserAuditDemo", modelDTO);

		JSONObject result = _userAuditProcessor.getModelDTOJSONObject(payload);

		assertNotNull(result);
		assertEquals(1L, result.optLong("id"));
	}

	@Test
	public void testGetModelDTOFallbackObjectEntry() {
		JSONObject entry = new JSONObject();

		entry.put("id", 7L);

		JSONObject payload = new JSONObject();

		payload.put("objectEntry", entry);

		JSONObject modelDTO = _userAuditProcessor.getModelDTOJSONObject(
			payload);

		assertEquals(7L, _userAuditProcessor.getObjectEntryId(modelDTO));
	}

	@Test
	public void testGetModelDTONone() {
		JSONObject payload = new JSONObject();

		payload.put("foo", "bar");

		assertNull(_userAuditProcessor.getModelDTOJSONObject(payload));
	}

	private final UserAuditProcessor _userAuditProcessor =
		new UserAuditProcessor();

}

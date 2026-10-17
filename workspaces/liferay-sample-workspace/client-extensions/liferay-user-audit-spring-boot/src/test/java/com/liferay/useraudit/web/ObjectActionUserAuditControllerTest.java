package com.liferay.useraudit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.useraudit.service.HeadlessAdminUserClient;
import com.liferay.useraudit.service.UserAuditProcessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

public class ObjectActionUserAuditControllerTest {

	@BeforeEach
	public void setUp() {
		_headlessAdminUserClient = Mockito.mock(HeadlessAdminUserClient.class);

		_objectActionUserAuditController =
			new ObjectActionUserAuditController(
				_headlessAdminUserClient, new UserAuditProcessor());

		ReflectionTestUtils.setField(
			_objectActionUserAuditController,
			"_oAuthApplicationHeadlessServerERC", "oahs-erc");
	}

	@Test
	public void testFetchesBothUsersWhenDifferent() {
		when(
			_headlessAdminUserClient.getUserAccount("oahs-erc", 20125L)
		).thenReturn(
			"{\"id\":20125,\"name\":\"Jane\",\"emailAddress\":\"jane@x.com\"}"
		);
		when(
			_headlessAdminUserClient.getUserAccount("oahs-erc", 20124L)
		).thenReturn(
			"{\"id\":20124,\"name\":\"John\",\"emailAddress\":\"john@x.com\"}"
		);

		String json =
			"{\"userId\":20125,\"objectActionTriggerKey\":\"onAfterUpdate\"," +
				"\"modelDTOUserAuditDemo\":{\"id\":99,\"creator\":{\"id\":" +
					"20124}}}";

		ResponseEntity<String> responseEntity =
			_objectActionUserAuditController.post(null, json);

		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());

		verify(_headlessAdminUserClient).getUserAccount("oahs-erc", 20125L);
		verify(_headlessAdminUserClient).getUserAccount("oahs-erc", 20124L);
	}

	@Test
	public void testFetchesOnceWhenCreatorEqualsUpdater() {
		when(
			_headlessAdminUserClient.getUserAccount("oahs-erc", 20125L)
		).thenReturn(
			"{\"id\":20125,\"name\":\"Jane\"}"
		);

		String json =
			"{\"userId\":20125,\"modelDTOUserAuditDemo\":{\"id\":5," +
				"\"creator\":{\"id\":20125}}}";

		_objectActionUserAuditController.post(null, json);

		verify(_headlessAdminUserClient).getUserAccount("oahs-erc", 20125L);
		verify(
			_headlessAdminUserClient, never()
		).getUserAccount(
			"oahs-erc", 20124L
		);
	}

	private HeadlessAdminUserClient _headlessAdminUserClient;
	private ObjectActionUserAuditController _objectActionUserAuditController;

}

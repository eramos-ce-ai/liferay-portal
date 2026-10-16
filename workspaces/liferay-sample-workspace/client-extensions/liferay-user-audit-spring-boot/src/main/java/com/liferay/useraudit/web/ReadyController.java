package com.liferay.useraudit.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness and readiness probe endpoint. Excluded from OAuth in
 * application-default.properties.
 */
@RequestMapping("/ready")
@RestController
public class ReadyController {

	@GetMapping
	public ResponseEntity<String> get() {
		return new ResponseEntity<>("READY", HttpStatus.OK);
	}

}

package com.example.serverprovision.convention.fixture.sample.dto.response;

/** 구조 규칙 고정 예제: mac · ip 를 String 으로 둔 응답(A6). */
record NetworkResponse(String macAddress, String ipAddress, String hostname) {
}

/** 구조 규칙 고정 예제: mac 을 값 객체로 둔 응답(통과). */
record TypedNetworkResponse(MacValue mac) {
	record MacValue(String value) {
	}
}

package com.example.serverprovision.convention.fixture.sample.dto.request;

/** 구조 규칙 고정 예제: 이름이 Request 로 끝나는 요청(통과). */
record SampleRequest(String name) {
}

/** 구조 규칙 고정 예제: 요청 패키지에 있지만 Request 로 끝나지 않는 클래스(A4). */
record SampleQuery(String keyword) {
}

/** 구조 규칙 고정 예제: DTO 접미사(A4). */
record SampleRequestDto(String name) {
}

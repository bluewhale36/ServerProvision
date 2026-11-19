package com.example.serverprovision.convention.fixture.management.sample.service;

import org.springframework.beans.factory.ObjectProvider;

/** 구조 규칙 고정 예제: 이름 확인 도구. 규칙은 단순 이름으로 알아본다. */
interface TypedNameVerifier {
}

/** 구조 규칙 고정 예제: 스캐너 계약. */
interface SampleMarkableScanner {
}

/** 구조 규칙 고정 예제: 이름 확인 도구와 ObjectProvider 를 주입받는 management 서비스(A5). */
class InjectingService {
	private TypedNameVerifier verifier;
	private ObjectProvider<String> provider;
}

/** 구조 규칙 고정 예제: 스캐너 구현체는 A5 대상이 아니다. */
class SampleScanner implements SampleMarkableScanner {
	private TypedNameVerifier verifier;
}

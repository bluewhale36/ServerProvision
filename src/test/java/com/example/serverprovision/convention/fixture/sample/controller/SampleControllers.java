package com.example.serverprovision.convention.fixture.sample.controller;

import com.example.serverprovision.convention.fixture.sample.entity.SampleEntity;
import com.example.serverprovision.convention.fixture.sample.repository.SampleRepository;
import org.springframework.transaction.annotation.Transactional;

/** 구조 규칙 고정 예제: 클래스에 @Transactional 을 둔 컨트롤러(A1). */
@Transactional
class TransactionalController {
}

/** 구조 규칙 고정 예제: 메서드에 @Transactional 을 두고 저장소 · 엔티티를 직접 쓰는 컨트롤러(A1 · A2 · A3). */
class LeakyController {
	private SampleRepository repository;

	@Transactional
	void save(String name, int[] counts) {
		repository.save(new SampleEntity());
	}
}

/** 구조 규칙 고정 예제: 규칙을 모두 지키는 컨트롤러. */
class CleanController {
	String show() {
		return "view";
	}
}

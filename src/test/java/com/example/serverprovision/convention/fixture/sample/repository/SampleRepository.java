package com.example.serverprovision.convention.fixture.sample.repository;

/** 구조 규칙 고정 예제: 컨트롤러가 직접 쓰면 안 되는 저장소. */
public interface SampleRepository {
	void save(Object value);
}

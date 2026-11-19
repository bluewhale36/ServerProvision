package com.example.serverprovision.convention.fixture.sample.entity;

/** 구조 규칙 고정 예제: 컨트롤러가 직접 다루면 안 되는 엔티티. mac 을 String 으로 둔 위반(A6)도 함께 든다. */
public class SampleEntity {
	private String mac;
	private static final String ip = "constant";
}

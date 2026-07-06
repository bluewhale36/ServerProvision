package com.example.serverprovision.provisioning.setting.dto.response;

/**
 * 세팅 정의서 생성(201)·수정(200) 응답. 생성 시 {@code id} 는 Location 헤더 URI 조립에도 쓰인다.
 * (U2-3 Q1 — 상태 개념 제거: 정의서는 재사용 템플릿, 실행 상태는 execution 소유)
 */
public record SettingSaveResponse(
        Long id,
        String name
) {
}

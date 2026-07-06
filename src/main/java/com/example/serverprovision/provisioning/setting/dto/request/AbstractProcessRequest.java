package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 세팅 정의서의 개별 프로비저닝 단계 — 1단 다형 베이스.
 *
 * <p>JSON 의 {@code "type"} 판별자(= {@link SettingProcessType} 상수명)로 concrete 타입이 결정된다.
 * 판별자는 레거시의 {@code BASIC_UPDATES}(복수형 불일치)를 {@code BASIC_UPDATE} 로 정정했다 —
 * renew 에 기존 저장 데이터가 없는 지금이 정정 적기다.</p>
 *
 * <p><b>판별자 해석은 {@link ProcessRequestDeserializer} 가 담당한다</b> — Jackson 3 이
 * 2단 중첩 {@code @JsonTypeInfo} 를 체이닝하지 않아 어노테이션 방식 대신 모듈 등록 방식을 쓴다
 * (wire 계약은 동일). 새 단계 타입 추가 = {@link SettingProcessType} 상수 + 해석기 맵 1항목 +
 * concrete 클래스 — 분기문 확장은 발생하지 않는다.</p>
 */
public abstract class AbstractProcessRequest {

    /**
     * 이 단계의 타입 — {@code "type"} 판별자와 1:1 인 다형 accessor. 화면(요약 배지·상세 분기)과
     * 서비스가 instanceof 사다리 없이 타입을 읽는 단일 지점이며, 직렬화 시 판별자 속성으로 그대로 쓰인다
     * (수정 폼 pre-fill JSON 이 다시 해석기로 왕복 가능해야 한다).
     */
    @JsonProperty("type")
    public abstract SettingProcessType processType();

    /**
     * 이 단계가 참조하는 BIOS 세팅 템플릿 id — 조인 테이블 파생(U2-2-3 D1) 전용 다형 accessor.
     * wire 에는 나가지 않으며(@JsonIgnore — BasicSetting 은 자체 필드로 직렬화), 참조가 없는
     * 타입은 빈 목록이다. 엔티티가 instanceof 없이 파생 행을 채우게 한다(processType() 관용구).
     */
    @JsonIgnore
    public List<Long> referencedBiosSettingTemplateIds() {
        return List.of();
    }
}

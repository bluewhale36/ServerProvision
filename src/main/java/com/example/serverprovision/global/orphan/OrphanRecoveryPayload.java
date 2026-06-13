package com.example.serverprovision.global.orphan;

import com.example.serverprovision.global.marker.ResourceType;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 격리된 오펀 자원의 재등록에 필요한 도메인별 데이터 (durable, {@code orphan_quarantine.payload} JSON 컬럼).
 *
 * <p>R1-4-4 — 공통 saga 인프라가 보존하는 "재시도용 페이로드". 도메인은 자신의 구현체(예: ISO 의
 * {@code IsoRecoveryPayload}) 를 제공하고, {@link OrphanRecoverySpi#relaunch} 에서 이를 캐스팅해 등록을 재구성한다.
 * 공통 격리 컬럼(parentId · resolvedPath · originalFilename · uploadedFile)으로 표현되지 않는
 * <b>도메인 전용 값만</b> 담는다.</p>
 *
 * <p><b>다형성 직렬화 — {@code Id.CLASS} 채택 사유</b> : 동프로젝트 {@code PurgeLogDetails} 는
 * {@code Id.NAME + @JsonSubTypes} 를 쓰지만 그 구현체(Success/Failed)가 같은 패키지라 가능했다. 본 payload 는
 * <b>도메인 횡단</b>(ISO/BIOS/BMC/…)이라, {@code Id.NAME + @JsonSubTypes} 로 가면 global 인터페이스가 각 도메인 구현체를
 * 등록해야 해 <b>레이어 역의존(global→management)</b>이 생긴다. 런타임 {@code registerSubtypes} 는 정적 Converter 와
 * 맞물려 추가 기계장치가 필요하다. {@code Id.CLASS} 는 reflection 기반이라 <b>컴파일 의존 0 + 도메인 무수정 확장</b>을
 * 보장하고, 격리 row 가 단명(TTL 기본 7일, 보통 분 단위 해소)이라 FQCN 직렬화의 리네임 fragility 가 무해하다.</p>
 *
 * <p>Jackson 3 — 어노테이션은 {@code com.fasterxml.jackson.annotation.*}, 런타임 직렬화는 {@code tools.jackson.*}
 * (CLAUDE.md §기술 스택). 영속 Converter 는 CP3 에서 도입.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface OrphanRecoveryPayload {

	/** 이 페이로드가 속한 자원 종류 (SPI 라우팅 보조). */
	ResourceType resourceType();
}

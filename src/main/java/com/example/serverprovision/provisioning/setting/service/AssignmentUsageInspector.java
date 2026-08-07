package com.example.serverprovision.provisioning.setting.service;

/**
 * 세팅 정의서를 참조하는 <b>활성 할당</b>의 사용중 판정 Service Provider Interface(SPI — 도메인이 구현해
 * 끼우는 확장점).
 *
 * <p>순환 회피가 목적이다(DEC-D). {@code assignment → setting} 컴파일 의존은 이미 존재하지만
 * {@code setting → assignment} 는 없다. 정의서 삭제 시 "이 정의서를 참조하는 활성 할당 N개" 경고를
 * {@code setting} 이 {@code assignment} 를 직접 호출해 만들면 {@code setting ↔ assignment} 순환이
 * 재생성된다. 그래서 인터페이스는 {@code provisioning.setting} 이 소유하고, 구현은 이미 setting 에
 * 의존하는 {@code provisioning.assignment} 가 제공한다({@code OwnedPhasesProvider} ·
 * BIOS 세팅 템플릿 {@code SettingProcessTemplateUsageChecker} 와 동형).</p>
 *
 * <p>삭제는 차단이 아니라 <b>경고</b>다(DEC-C). 정의서 → 할당은 FK 없는 소프트참조라 정의서를 지워도 할당
 * 스냅샷은 생존하므로, 이 카운트는 정보성 안내이지 삭제를 막는 무결성 근거가 아니다.</p>
 */
public interface AssignmentUsageInspector {

    /** 이 정의서를 참조하는 활성 할당 수(경고 문구용, {@code supersededAt IS NULL}). */
    long countReferencing(Long definitionId);

    /** 참조 여부 저비용 분기 — 카운트가 불필요한 곳의 존재 여부 판정. */
    boolean isReferenced(Long definitionId);
}

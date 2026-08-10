package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;

import java.util.List;

/**
 * 단계 타입별 참조 검사기 SPI (U2-3-1) — {@link SettingProcessType} 키 맵으로 dispatch 된다
 * ({@code ProcessRequestDeserializer} 등록 맵의 서버측 대칭).
 *
 * <p>쓰기 가드({@link #validateReferences})와 조회 표시({@link #describeDeprecatedReferences})를
 * 한 타입에 응집한다 — 소비자(Command/Query)는 다르지만 "이 타입이 참조하는 자원과 그 해석"이라는
 * 지식은 하나이기 때문(소비자별 인터페이스 분리는 같은 순회 코드를 다시 두 곳에 만든다).</p>
 */
public interface ProcessReferenceInspector {

    /** dispatch 키 — payload 판별자와 동일 축. */
    SettingProcessType target();

    /**
     * 쓰기 가드 — 참조 자원의 실존(404)/enabled(409 field-bound)/정합(400 field-bound)을 검증한다.
     * deprecated 는 거절하지 않는다(사용자 확정 — 화면 modal·뱃지로만 안내).
     * {@code context} 는 형제 단계가 필요한 cross-process 영속 검증용(U2-2-3 D3) — 불필요하면 무시.
     */
    void validateReferences(AbstractProcessRequest process, ProcessValidationContext context);

    /** 조회 표시 — 이 단계가 사용 중인 deprecated 자원의 표시명 목록(상세 카드 뱃지 데이터). */
    List<String> describeDeprecatedReferences(AbstractProcessRequest process);

    /**
     * MK4-2 — 이 단계가 지정하는 <b>파일 실체가 있는 자원</b>들의 식별자.
     *
     * <p>드리프트는 디스크의 파일과 마커를 대조해 생기므로 파일이 없는 참조(메인보드 모델 ·
     * BIOS 세팅 템플릿 · OS 버전 같은 논리 자원)는 여기 담지 않는다. 담아도 대조할 드리프트가 없어
     * 쓰이지 않고, 담기 시작하면 "무엇을 담아야 하는가" 의 기준이 흐려진다.</p>
     *
     * <p>기본 구현을 두지 않는다. 새 단계 타입이 생기면 파일 자원을 참조하는지 여부를 반드시 한 번
     * 답하게 하려는 것이다 — 빈 목록을 돌려주는 기본 구현을 두면, 자원을 참조하는 새 타입이
     * 조용히 "사용 중 아님" 으로 집계되어 그 자원의 드리프트가 실제보다 낮은 순서로 밀린다.</p>
     *
     * @return 참조하는 파일 자원들. 없으면 빈 목록(널 반환 금지)
     */
    List<ResourceKey> referencedResources(AbstractProcessRequest process);
}

package com.example.serverprovision.provisioning.setting.service.reference;

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
}

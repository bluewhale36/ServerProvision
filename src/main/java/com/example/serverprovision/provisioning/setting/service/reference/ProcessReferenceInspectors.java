package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 검사기 1급 컬렉션 — 타입 키 색인을 한 번만 구성해 Command/Query 양쪽에 공급한다
 * (서비스마다 색인을 반복하면 그 자체가 중복).
 *
 * <p>중복 {@code target()} 은 기동 실패(toUnmodifiableMap), 미등록 타입 조회는
 * {@link IllegalStateException} — BASIC_SETTING no-op 선생성으로 전 타입이 등록되므로
 * 미스는 "신규 타입에 검사기를 빠뜨린 설정 결함"의 신호다(D4 개정, fail-fast).</p>
 */
@Component
public class ProcessReferenceInspectors {

    private final Map<SettingProcessType, ProcessReferenceInspector> byType;

    public ProcessReferenceInspectors(List<ProcessReferenceInspector> inspectors) {
        this.byType = inspectors.stream()
                .collect(Collectors.toUnmodifiableMap(ProcessReferenceInspector::target, Function.identity()));
    }

    public ProcessReferenceInspector inspectorFor(SettingProcessType type) {
        ProcessReferenceInspector inspector = byType.get(type);
        if (inspector == null) {
            throw new IllegalStateException("단계 타입에 등록된 참조 검사기가 없습니다: " + type);
        }
        return inspector;
    }
}

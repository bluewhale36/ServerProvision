package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 정의서 선택 모달 한 판 (U3-5-b) — 좌측 목록과 우측 상세 패널을 함께 담는다.
 *
 * <p>조립이 서비스가 아니라 이 응답의 정적 팩터리에 있는 이유는 <b>조회가 아니라 맞추기</b>이기 때문이다.
 * 두 재료(할당 폼 · 정의서 상세)는 각자의 서비스가 이미 만들어 오고, 여기서 하는 일은 id 로 짝을 짓는
 * 것뿐이라 트랜잭션도 리포지토리도 필요 없다. 이것을 {@code AssignmentQueryService} 에 두면 그 서비스가
 * {@code SettingQueryService} 를 알아야 하는데, setting 은 이미 {@code AssignmentUsageInspector} 로
 * assignment 를 참조하고 있어 패키지가 양방향이 된다(R7 이 없앤 형태).</p>
 */
public record AssignmentPickerResponse(
        List<DefinitionPickerItemResponse> items
) {

    /**
     * 선택지에 각자의 상세를 붙인다.
     *
     * <p>상세가 없는 선택지는 <b>떨군다.</b> 두 재료를 읽는 사이에 그 정의서가 삭제됐다는 뜻이고, 삭제 ·
     * 비활성 정의서를 목록에서 빼는 것은 이미 정해진 규칙이다(U3-2-b DEC-G). 남겨 두면 고를 수는 있는데
     * 우측이 비어 있는 항목이 된다. 억지로 밀어 넣어도 서버 가드가 409 로 거절한다.</p>
     */
    public static AssignmentPickerResponse of(AssignmentFormResponse form, List<SettingDetailResponse> details) {
        Map<Long, SettingDetailResponse> byId = details.stream()
                .collect(Collectors.toMap(SettingDetailResponse::id, Function.identity()));
        return new AssignmentPickerResponse(form.options().stream()
                .filter(option -> byId.containsKey(option.id()))
                .map(option -> new DefinitionPickerItemResponse(option, byId.get(option.id())))
                .toList());
    }

    /** 고를 수 있는 정의서가 하나라도 있는가 — 없으면 모달이 그 사실을 안내한다. */
    public boolean hasSelectable() {
        return items.stream().anyMatch(item -> !item.blocked());
    }
}

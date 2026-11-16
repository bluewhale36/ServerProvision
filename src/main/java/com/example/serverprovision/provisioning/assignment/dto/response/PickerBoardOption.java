package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 정의서 고르기 모달의 조회 조건 창 '대상 보드' 칩 하나(R20). 후보는 지금 고를 수 있는 정의서들이 요구하는 보드뿐이다 —
 * 등록된 보드 전부를 늘어놓으면 어느 칩을 골라도 결과가 비는 칩이 생긴다.
 */
public record PickerBoardOption(Long id, String name) {

    /** 정의서들이 요구하는 보드를 이름순으로 한 번씩. 보드를 요구하지 않는 정의서(보드 무관)는 칩이 따로다. */
    public static List<PickerBoardOption> of(Collection<SettingSummaryResponse> definitions) {
        return definitions.stream()
                .filter(d -> d.requiredBoardModelId() != null)
                .map(d -> new PickerBoardOption(d.requiredBoardModelId(),
                        Objects.requireNonNullElse(d.requiredBoardModelName(), "#" + d.requiredBoardModelId())))
                .distinct()
                .sorted(Comparator.comparing(PickerBoardOption::name))
                .toList();
    }

    /** 보드를 요구하지 않는 정의서가 있는가 — 있을 때만 '보드 무관' 칩을 둔다. */
    public static boolean anyBoardless(Collection<SettingSummaryResponse> definitions) {
        return definitions.stream().anyMatch(d -> d.requiredBoardModelId() == null);
    }
}

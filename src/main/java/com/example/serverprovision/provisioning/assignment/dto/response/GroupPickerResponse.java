package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 그룹 일괄 할당 모달 한 판 (U3-5-c) — 좌측 목록과 우측 패널을 함께 담는다.
 *
 * <p>조립이 서비스가 아니라 이 응답의 정적 팩터리에 있는 이유는 U3-5-b 와 같다 — 하는 일이 조회가 아니라
 * <b>id 로 짝짓기</b>라 트랜잭션도 리포지토리도 필요 없고, {@code AssignmentQueryService} 에 두면 그
 * 서비스가 {@code SettingQueryService} 를 알아야 해서 {@code assignment} ↔ {@code setting} 이 양방향이
 * 된다(R7 이 없앤 형태).</p>
 *
 * @param memberCount 그룹 멤버 수. 좌측 목록이 "8 대" 만 적고 분모를 한 곳에서 알리기 위해 함께 싣는다
 */
public record GroupPickerResponse(
        int memberCount,
        List<GroupPickerItemResponse> items
) {

    /**
     * 미리보기에 각자의 정의서 상세를 붙인다.
     *
     * <p>상세가 없는 미리보기는 <b>떨군다</b> — 두 재료를 읽는 사이에 그 정의서가 삭제됐다는 뜻이고,
     * 삭제 · 비활성 정의서를 목록에서 빼는 것은 이미 정해진 규칙이다(U3-2-b DEC-G).</p>
     */
    public static GroupPickerResponse of(int memberCount,
                                         List<GroupApplyPreviewResponse> previews,
                                         List<SettingDetailResponse> details) {
        Map<Long, SettingDetailResponse> byId = details.stream()
                .collect(Collectors.toMap(SettingDetailResponse::id, Function.identity()));
        return new GroupPickerResponse(memberCount, previews.stream()
                .filter(preview -> byId.containsKey(preview.definitionId()))
                .map(preview -> new GroupPickerItemResponse(preview, byId.get(preview.definitionId())))
                .toList());
    }

    /** 한 대라도 붙일 수 있는 정의서가 있는가 — 없으면 모달이 그 사실을 안내한다. */
    public boolean hasSelectable() {
        return items.stream().anyMatch(item -> !item.blocked());
    }
}

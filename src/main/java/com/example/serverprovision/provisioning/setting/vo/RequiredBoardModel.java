package com.example.serverprovision.provisioning.setting.vo;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 세팅 정의서가 요구하는 메인보드 (U3-5-a) — 하드웨어 대조의 첫 번째 축.
 *
 * <p>펌웨어 업데이트와 BIOS 설정 단계는 보드를 {@code AUTO}(실행 시점 감지) 또는
 * {@code SPECIFIED}(특정 보드 고정)로 고른다. {@code SPECIFIED} 면 그 정의서는 <b>그 보드의 서버에만</b>
 * 적용할 수 있다 — 펌웨어는 보드별 바이너리이고 BIOS 세팅 템플릿은 보드 모델에 매여 있기 때문이다.</p>
 *
 * <p>{@code Long boardModelId} 를 맨손으로 들고 다니면 그 값이 서버의 보드인지 정의서가 요구하는 보드인지
 * 타입이 말해 주지 않는다. 이름 있는 값으로 세워 그 혼동을 없앤다.</p>
 *
 * <p><b>U4 가 디스크 구성 대조를 더할 때</b>는 이 클래스 옆에 {@code RequiredDiskLayout} 을 세운다.
 * 지금 공통 인터페이스를 뽑지 않는 것은 구현이 하나뿐이기 때문이며, 보드는 식별자 하나를 비교하지만
 * 디스크는 개수 · 용량 · 인터페이스를 함께 볼 가능성이 커서 <b>공통 형태가 실측으로 드러난 뒤에</b>
 * 뽑는 편이 맞기 때문이다. 그때 뽑아도 호출자는 바뀌지 않는다 —
 * 호출자는 {@code AssignmentBlockKind.evaluate} 만 부른다.</p>
 */
public record RequiredBoardModel(Long id, String name) {

    public RequiredBoardModel {
        Objects.requireNonNull(id, "요구 보드 모델 id 는 null 일 수 없습니다.");
    }

    /**
     * 단계 목록에서 요구 보드를 뽑는다 — 지정한 단계가 없거나 전부 {@code AUTO} 면 {@code null}.
     *
     * <p>{@code instanceof} 사다리를 놓지 않는다. 각 단계 타입이
     * {@link AbstractProcessRequest#requiredBoardModel()} 을 다형으로 답하므로, 보드를 고르는 단계 타입이
     * 늘어도 여기는 그대로다.</p>
     *
     * <p>정의서 하나가 요구하는 보드는 최대 하나다 — 펌웨어 업데이트와 BIOS 설정이 함께 있으면 같은
     * 선택이어야 한다는 것을 {@code SettingSaveRequest.isBoardSelectionConsistent} 가 이미 강제한다.
     * 그래서 처음 발견한 지정을 그대로 쓴다.</p>
     *
     * @param boardName 보드 모델명 조회. 이 값 객체가 리포지토리를 알지 않도록 호출자가 공급한다
     */
    public static RequiredBoardModel from(List<? extends AbstractProcessRequest> processes,
                                          Function<Long, String> boardName) {
        if (processes == null) {
            return null;
        }
        return processes.stream()
                .map(AbstractProcessRequest::requiredBoardModel)
                .filter(selection -> selection != null && !selection.isAuto())
                .map(BoardModelSelectionRequest::boardModelId)
                .filter(Objects::nonNull)
                .findFirst()
                .map(id -> new RequiredBoardModel(id, boardName.apply(id)))
                .orElse(null);
    }

    /**
     * 이 서버에 붙일 수 있는지 — 붙일 수 있으면 {@code null}, 아니면 <b>화면 안내이자 서버 거절 사유</b>다.
     *
     * <p><b>서버 보드를 아직 모르면 막지 않는다.</b> 수집 전 서버에 미리 정의서를 할당해 두고 부팅시키는
     * 흐름이 실재하며, 모른다는 이유로 막으면 그 흐름이 끊긴다. 대조하지 못했다는 사실은 화면이
     * 별도 표식으로 알린다.</p>
     */
    public String blockReasonFor(Long serverBoardModelId, String serverBoardModelName) {
        if (serverBoardModelId == null || id.equals(serverBoardModelId)) {
            return null;
        }
        return "이 정의서는 메인보드 " + name + " 전용입니다 — 이 서버는 "
                + (serverBoardModelName != null ? serverBoardModelName : "다른 보드") + " 입니다.";
    }
}

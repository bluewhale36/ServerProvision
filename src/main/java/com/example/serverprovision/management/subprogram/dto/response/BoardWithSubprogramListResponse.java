package com.example.serverprovision.management.subprogram.dto.response;

import com.example.serverprovision.management.board.enums.Vendor;

import java.util.List;

/**
 * Subprogram 페이지의 Miller C1 행 묶음.
 * <p>공용 분류는 {@link #boardId} 가 {@code null} + {@link #vendor}/{@link #vendorDisplayName}/{@link #modelName} 도 {@code null}.
 * 템플릿이 {@link #boardId} null 여부로 "공용" 라벨 분기.</p>
 */
public record BoardWithSubprogramListResponse(
        Long boardId,
        Vendor vendor,
        String vendorDisplayName,
        String modelName,
        boolean isDeleted,
        List<SubprogramResponse> items
) {
    public boolean isCommonScope() {
        return boardId == null;
    }

    public static BoardWithSubprogramListResponse common(List<SubprogramResponse> items) {
        return new BoardWithSubprogramListResponse(null, null, null, null, false, items);
    }
}

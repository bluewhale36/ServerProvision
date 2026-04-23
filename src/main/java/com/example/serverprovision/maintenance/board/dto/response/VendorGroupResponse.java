package com.example.serverprovision.maintenance.board.dto.response;

import com.example.serverprovision.maintenance.board.enums.Vendor;

import java.util.List;

/**
 * Miller Columns 의 C1(제조사) + C2(모델 목록) 데이터 단위.
 * 같은 Vendor 를 가진 메인보드 모델들을 묶어 뷰에서 그룹 단위로 렌더한다.
 */
public record VendorGroupResponse(
        Vendor vendor,
        String displayName,
        List<BoardModelResponse> items
) {
    public static VendorGroupResponse of(Vendor vendor, List<BoardModelResponse> items) {
        return new VendorGroupResponse(vendor, vendor.getDisplayName(), items);
    }
}

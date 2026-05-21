package com.example.serverprovision.management.bios.dto.response;

import com.example.serverprovision.management.board.enums.Vendor;

import java.util.List;

/**
 * Miller C1 각 행의 데이터 묶음.
 * BoardModel 하나와 그 하위 BIOS 번들 리스트를 함께 내려준다. Vendor 라벨은 뷰에서 inline 표시.
 * {@code isDeleted} 는 BoardModel 자체의 soft 삭제 상태 — 휴지통 보기에서만 노출.
 */
public record BoardWithBiosListResponse(
		Long id,
		Vendor vendor,
		String vendorDisplayName,
		String modelName,
		boolean isDeleted,
		List<BiosResponse> biosList
) {

}

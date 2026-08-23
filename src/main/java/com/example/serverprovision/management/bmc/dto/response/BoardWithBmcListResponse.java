package com.example.serverprovision.management.bmc.dto.response;

import com.example.serverprovision.management.board.enums.Vendor;

import java.util.List;

/**
 * Miller C1 각 행의 데이터 묶음.
 */
public record BoardWithBmcListResponse(
		Long id,
		Vendor vendor,
		String vendorDisplayName,
		String modelName,
		boolean isDeleted,
		/** "최신" = 순위 1위 enabled 후보(E2-1-a) — resolve 의 LATEST 와 같은 술어. 후보 0 이면 null. */
		Long latestBmcId,
		List<BmcResponse> bmcList
) {

}

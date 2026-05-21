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
		List<BmcResponse> bmcList
) {

}

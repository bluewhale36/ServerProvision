package com.example.serverprovision.management.raidcard.dto.response;

import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;

import java.util.List;

/**
 * Miller Columns 의 C1(제조사) + C2(카드 목록) 데이터 단위.
 * 같은 제조사의 RAID 카드들을 묶어 뷰에서 그룹 단위로 렌더한다 (Board 의 VendorGroupResponse 선례).
 */
public record RaidCardVendorGroupResponse(
		RaidCardVendor vendor,
		String displayName,
		List<RaidCardResponse> items
) {

	public static RaidCardVendorGroupResponse of(RaidCardVendor vendor, List<RaidCardResponse> items) {
		return new RaidCardVendorGroupResponse(vendor, vendor.getDisplayName(), items);
	}
}

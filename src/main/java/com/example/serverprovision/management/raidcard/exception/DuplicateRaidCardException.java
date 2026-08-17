package com.example.serverprovision.management.raidcard.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;

/**
 * 동일 (vendor, modelName) 조합이 이미 "살아 있는"(비삭제 · 비 Deprecated) 레코드로 존재할 때 던진다.
 * DB 생성 컬럼 유니크 인덱스 {@code uk_raid_card_active_identity} 와 이중 가드를 이룬다(MA7 D7).
 * <p>modelName 필드 직결 ({@code data-error-field="modelName"} 매핑).</p>
 */
public class DuplicateRaidCardException extends FieldBoundConflictException {

	public DuplicateRaidCardException(RaidCardVendor vendor, String modelName) {
		super("이미 등록된 RAID 카드입니다. %s %s".formatted(vendor.getDisplayName(), modelName), "modelName");
	}
}

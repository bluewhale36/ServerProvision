package com.example.serverprovision.management.raidcard.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.global.lifecycle.IdentityConflictSubject;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;

/**
 * 동일 (vendor, modelName) 조합이 이미 있을 때 던진다. HF30 부터 휴지통 · Deprecated 행도 정체를 점유한다(MA7 D7 의
 * 'Deprecated 공존' 을 사용자 결정으로 뒤집음 — 메인보드 HF26 과 같은 규칙). DB {@code uk_raid_card_vendor_model_name} 과 이중 가드.
 * <p>modelName 필드 직결 ({@code data-error-field="modelName"} 매핑).</p>
 */
public class DuplicateRaidCardException extends FieldBoundConflictException {

	public DuplicateRaidCardException(RaidCardVendor vendor, String modelName) {
		super("이미 등록된 RAID 카드입니다. %s %s".formatted(vendor.getDisplayName(), modelName), "modelName");
	}

	private DuplicateRaidCardException(String message) {
		super(message, "modelName");
	}

	/** 등록 충돌(HF30) — 기존 행의 상태에 맞는 다음 행동(복구 · 영구 삭제 · Deprecated 해제)을 안내한다. */
	public static DuplicateRaidCardException againstRegistration(RaidCard existing) {
		return new DuplicateRaidCardException(stageOf(existing).registrationConflict(subjectOf(existing)));
	}

	/** 이름 수정 충돌(HF30) — 다음 행동은 다른 이름을 쓰는 것(휴지통이면 그 카드의 영구 삭제도). */
	public static DuplicateRaidCardException againstRename(RaidCard existing) {
		return new DuplicateRaidCardException(stageOf(existing).renameConflict(subjectOf(existing)));
	}

	private static LifecycleStage stageOf(RaidCard card) {
		return LifecycleStage.of(card.isDeprecated(), card.isDeleted());
	}

	private static IdentityConflictSubject subjectOf(RaidCard card) {
		return new IdentityConflictSubject("RAID 카드", "RAID 카드가", "카드를",
				card.getVendor().getDisplayName() + " " + card.getModelName());
	}
}

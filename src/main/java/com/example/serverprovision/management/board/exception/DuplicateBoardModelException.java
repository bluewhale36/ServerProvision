package com.example.serverprovision.management.board.exception;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.global.lifecycle.IdentityConflictSubject;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.board.enums.Vendor;

/**
 * 동일 (Vendor, modelName) 조합이 이미 있을 때 던진다. DB 의 {@code uk_board_model_vendor_model_name} 복합 UNIQUE(HF26)와
 * 이중 가드를 이룬다. 휴지통 행도 정체를 점유하므로 같은 모델은 새로 등록하지 않고 복구 · Deprecated 해제로 다룬다 —
 * {@link #against} 가 기존 행의 상태에 맞는 다음 행동을 문구에 싣는다.
 * <p>S4 — modelName 필드 직결 ({@code data-error-field="modelName"} 매핑).</p>
 */
public class DuplicateBoardModelException extends FieldBoundConflictException {

	public DuplicateBoardModelException(Vendor vendor, String modelName) {
		super("이미 등록된 메인보드 모델입니다. %s %s".formatted(vendor.getDisplayName(), modelName), "modelName");
	}

	private DuplicateBoardModelException(String message) {
		super(message, "modelName");
	}

	/**
	 * 등록 충돌(HF26) — 기존 행이 휴지통 · Deprecated · 활성 중 어디에 있느냐에 따라 다음 행동을 안내한다.
	 * 문장은 HF30 부터 {@link LifecycleStage#registrationConflict} 가 소유한다(OS 버전 · RAID 카드와 같은 SSOT, 문구는 HF26 그대로).
	 */
	public static DuplicateBoardModelException againstRegistration(BoardModel existing) {
		return new DuplicateBoardModelException(stageOf(existing).registrationConflict(subjectOf(existing)));
	}

	/**
	 * 이름 수정 충돌(HF26 CP5 D-2) — 등록과 같은 판정이지만 다음 행동이 다르다(복구 · Deprecated 해제가 아니라 다른 이름).
	 * 수정 폼은 이 문구를 모델명 칸 오류로 그린다. 문장은 {@link LifecycleStage#renameConflict}.
	 */
	public static DuplicateBoardModelException againstRename(BoardModel existing) {
		return new DuplicateBoardModelException(stageOf(existing).renameConflict(subjectOf(existing)));
	}

	private static LifecycleStage stageOf(BoardModel board) {
		return LifecycleStage.of(board.isDeprecated(), board.isDeleted());
	}

	private static IdentityConflictSubject subjectOf(BoardModel board) {
		return new IdentityConflictSubject("메인보드 모델", "메인보드 모델이", "모델을",
				board.getVendor().getDisplayName() + " " + board.getModelName());
	}
}

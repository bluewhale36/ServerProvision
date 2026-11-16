package com.example.serverprovision.management.board.exception;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.global.exception.FieldBoundConflictException;
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

	/** 등록 충돌(HF26) — 기존 행이 휴지통 · Deprecated · 활성 중 어디에 있느냐에 따라 다음 행동을 안내한다. */
	public static DuplicateBoardModelException againstRegistration(BoardModel existing) {
		String name = existing.getVendor().getDisplayName() + " " + existing.getModelName();
		if (existing.isDeleted()) {
			return new DuplicateBoardModelException(
					"휴지통에 같은 메인보드 모델이 있습니다(" + name + "). 휴지통에서 복구하거나 영구 삭제한 뒤 등록하십시오.");
		}
		if (existing.isDeprecated()) {
			return new DuplicateBoardModelException(
					"Deprecated 상태로 등록된 같은 메인보드 모델이 있습니다(" + name + "). 목록에서 Deprecated 를 해제하십시오.");
		}
		return new DuplicateBoardModelException(existing.getVendor(), existing.getModelName());
	}

	/**
	 * 이름 수정 충돌(HF26 CP5 D-2) — 등록과 같은 판정이지만 다음 행동이 다르다(복구 · Deprecated 해제가 아니라 다른 이름).
	 * 수정 폼은 이 문구를 모델명 칸 오류로 그린다.
	 */
	public static DuplicateBoardModelException againstRename(BoardModel existing) {
		String name = existing.getVendor().getDisplayName() + " " + existing.getModelName();
		if (existing.isDeleted()) {
			return new DuplicateBoardModelException(
					"휴지통에 같은 이름의 메인보드 모델이 있습니다(" + name + "). 다른 이름을 쓰거나 휴지통의 모델을 영구 삭제하십시오.");
		}
		if (existing.isDeprecated()) {
			return new DuplicateBoardModelException(
					"Deprecated 상태로 등록된 같은 이름의 메인보드 모델이 있습니다(" + name + "). 다른 이름을 쓰십시오.");
		}
		return new DuplicateBoardModelException(existing.getVendor(), existing.getModelName());
	}
}

package com.example.serverprovision.management.board.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.management.board.enums.Vendor;

/**
 * 동일 (Vendor, modelName) 조합이 이미 활성 레코드로 존재할 때 던진다.
 * DB 의 {@code uk_vendor_model_name} 유니크 제약과 이중 가드를 이룬다 —
 * 삭제된 동일 조합을 재등록하려 할 때도 DB 제약 때문에 사실상 실패하므로, 복구 경로를 사용해야 한다.
 * <p>S4 — modelName 필드 직결 ({@code data-error-field="modelName"} 매핑).</p>
 */
public class DuplicateBoardModelException extends FieldBoundConflictException {

	public DuplicateBoardModelException(Vendor vendor, String modelName) {
		super("이미 등록된 메인보드 모델입니다. %s %s".formatted(vendor.getDisplayName(), modelName), "modelName");
	}
}

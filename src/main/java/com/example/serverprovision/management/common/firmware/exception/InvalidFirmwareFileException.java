package com.example.serverprovision.management.common.firmware.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * R12-1 — BIOS 펌웨어 파일 등록 입력이 부적합할 때의 필드 직결 400 예외.
 *
 * <p>사유 4종을 메시지로 구분하고 fieldName 을 생성자 인자로 받는다 — 사유마다 sub-class 를 늘리면
 * 분기가 도메인 수만큼 자라므로 단일 클래스로 둔다(프런트가 안정 코드로 분기할 필요 없음).</p>
 * <ul>
 *   <li>금지 파일명(PFR1.RBU · PFR2.RBU) — 업로드 파일 또는 경로 파일명</li>
 *   <li>경로가 {@code /} 로 끝나는데 업로드 파일이 없음</li>
 *   <li>업로드 없는 등록(claim)인데 경로에 파일이 실재하지 않음</li>
 *   <li>부모 디렉토리 비배타 — 마커 · 무시 가능 파일 외 다른 파일 존재</li>
 * </ul>
 */
public class InvalidFirmwareFileException extends FieldBoundBadRequestException {

	public InvalidFirmwareFileException(String message, String fieldName) {
		super(message, fieldName);
	}
}

package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 이미 환경·패키지 그룹이 추출된 ISO 에 대해 재추출을 시도했을 때 발생.
 * 재추출은 시간 낭비이자 의미 없는 연산이므로 Service 진입부에서 차단한다.
 * 재추출이 필요하면 ISO 를 soft delete 한 후 다시 등록하거나 별도의 "재추출" 기능을 통해 명시적으로 수행해야 한다.
 */
public class AlreadyExtractedException extends ConflictException {

	public AlreadyExtractedException(Long isoId) {
		super("이미 추출이 완료된 ISO 입니다. isoId=" + isoId);
	}
}

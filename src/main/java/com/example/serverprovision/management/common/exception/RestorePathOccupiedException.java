package com.example.serverprovision.management.common.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK3 — restore 검증 (3) 실패 : DB 의 원래 경로 위치에 동일 이름의 다른 파일이 점유 중.
 * <p>점유 자원이 active 등록 자원이라면 nudge 흐름으로, 외부 파일이라면 사용자 결정 (overwrite / 다른 이름 / 취소).</p>
 */
public class RestorePathOccupiedException extends ConflictException {

    public RestorePathOccupiedException(String occupiedPath) {
        super("복원할 경로가 이미 점유되어 있습니다 : " + occupiedPath);
    }
}

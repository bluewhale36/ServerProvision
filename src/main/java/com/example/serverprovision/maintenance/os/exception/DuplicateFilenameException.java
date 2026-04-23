package com.example.serverprovision.maintenance.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 업로드 대상 경로에 이미 동일 이름의 파일이 파일시스템 상 존재할 때 던진다.
 * 덮어쓰기로 인한 실수 방지를 위해 하드 거절한다 — 사용자가 의도적으로 교체하려면 기존 파일을
 * 먼저 정리해야 한다.
 */
public class DuplicateFilenameException extends ConflictException {
    public DuplicateFilenameException(String path) {
        super("해당 경로에 이미 같은 이름의 파일이 존재합니다 : " + path);
    }
}

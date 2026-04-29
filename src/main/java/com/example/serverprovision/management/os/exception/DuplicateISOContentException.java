package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 업로드한 ISO 파일의 바이트 내용(SHA-256) 이 이미 등록된 활성 ISO 와 동일할 때 던진다.
 * 서로 다른 경로·이름으로 올려도 내용이 같으면 디스크 낭비가 발생하므로 차단하는 것이 목적이다.
 */
public class DuplicateISOContentException extends ConflictException {

    public DuplicateISOContentException(String existingIsoPath) {
        super("이미 등록된 ISO 와 내용이 동일합니다. 기존 경로: " + existingIsoPath);
    }
}

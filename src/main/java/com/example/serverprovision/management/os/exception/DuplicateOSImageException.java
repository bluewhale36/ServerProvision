package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.management.os.enums.OSName;

/**
 * 동일 (OSName, osVersion) 조합이 이미 활성 레코드로 존재할 때 던진다.
 * 삭제된 동일 조합은 중복으로 보지 않는다 — 재등록은 새 레코드로 진행된다.
 */
public class DuplicateOSImageException extends ConflictException {

    public DuplicateOSImageException(OSName osName, String osVersion) {
        super("이미 등록된 OS 버전입니다. %s %s".formatted(osName.getDisplayName(), osVersion));
    }
}

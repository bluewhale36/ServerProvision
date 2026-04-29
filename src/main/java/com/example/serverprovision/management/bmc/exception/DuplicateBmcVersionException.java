package com.example.serverprovision.management.bmc.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 같은 BoardModel 범위에서 활성 BMC 의 version 이 중복될 때 던진다.
 * <p>S4 — version 필드 직결.</p>
 */
public class DuplicateBmcVersionException extends FieldBoundConflictException {

    public DuplicateBmcVersionException(Long boardId, String version) {
        super("같은 메인보드에 이미 같은 버전의 BMC 펌웨어가 등록되어 있습니다. boardId=" + boardId + ", version=" + version, "version");
    }
}

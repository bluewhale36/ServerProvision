package com.example.serverprovision.maintenance.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 업로드 대상 경로의 상위 디렉토리가 존재하지 않고 사용자가 "디렉토리 생성 허용" 옵션을 체크하지 않은 경우 던진다.
 * 사용자는 경로를 바로잡거나 체크박스를 체크한 뒤 다시 시도해야 한다.
 */
public class DirectoryMissingException extends ConflictException {
    public DirectoryMissingException(String parentPath) {
        super("상위 디렉토리가 존재하지 않습니다 : " + parentPath
                + " · '디렉토리 생성 허용' 옵션을 체크하면 자동으로 생성됩니다.");
    }
}

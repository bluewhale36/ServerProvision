package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 기존 디렉토리 등록 모드에서 사용. 지정 디렉토리에 등록할 만한 콘텐츠 (무시 대상 외 파일) 가 하나도 없을 때.
 */
public class EmptyTargetDirectoryException extends FieldBoundConflictException {
    public EmptyTargetDirectoryException(String path) {
        super("지정한 디렉토리에 등록할 콘텐츠가 없습니다 : " + path,
                "targetDirectory");
    }
}

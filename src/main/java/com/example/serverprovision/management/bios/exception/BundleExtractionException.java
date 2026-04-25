package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * 번들 전개(zip unzip 또는 폴더 파일 복사) 도중 I/O 또는 무결성 오류가 발생한 경우.
 * zip slip 탐지 · 디스크 여유 부족 · 권한 오류 등을 포괄.
 */
public class BundleExtractionException extends DomainException {
    public BundleExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

    public BundleExtractionException(String message) {
        super(message);
    }
}

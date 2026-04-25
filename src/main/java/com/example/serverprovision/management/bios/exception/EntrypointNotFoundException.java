package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 번들 트리에서 진입점을 판별할 수 없는 경우 — 자동 탐지 규칙(트리 루트의 f.nsh / flash.nsh /
 * 단일 *.nsh)에 해당하는 파일이 없고 override 도 지정되지 않음.
 */
public class EntrypointNotFoundException extends ConflictException {
    public EntrypointNotFoundException(String message) {
        super(message);
    }
}

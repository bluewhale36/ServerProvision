package com.example.serverprovision.execution.wininstall.catalog;

/**
 * WIM 헤더 · XML 이 기대 형식이 아닐 때 — 내부 전용. {@link WindowsImageCatalog} 가
 * {@link InstallSourceCondition#UNREADABLE} 판정으로 흡수하므로 HTTP 경계에 닿지 않는다.
 */
public class WimFormatException extends RuntimeException {

    public WimFormatException(String message) {
        super(message);
    }
}

package com.example.serverprovision.global.ui.exception;

import com.example.serverprovision.global.exception.NotFoundException;
import com.example.serverprovision.global.marker.ResourceType;

/**
 * S5-6-1 — modal fragment lazy-load 시 (resourceType, resourceId) 로 자원 lookup 실패.
 *
 * <p>{@code GET /ui/confirm-modal/{modalType}?resourceType=...&resourceId=...} 에서
 * 활성 / 휴지통 어느 쪽에도 없으면 본 예외. {@code @ControllerAdvice} 가 404 매핑.</p>
 */
public class ModalContextNotFoundException extends NotFoundException {

    public ModalContextNotFoundException(ResourceType resourceType, Long resourceId) {
        super("modal 대상 자원을 찾을 수 없습니다 : " + resourceType + "#" + resourceId);
    }
}

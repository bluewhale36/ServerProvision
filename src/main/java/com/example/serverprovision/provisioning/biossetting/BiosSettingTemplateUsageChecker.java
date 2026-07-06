package com.example.serverprovision.provisioning.biossetting;

/**
 * BIOS 세팅 템플릿의 사용중 판정 SPI (U2-2-3 D4) — biossetting 이 자기 삭제 규칙의 확장점으로
 * 소유하고, 사용처(setting 의 조인 테이블)가 구현을 제공한다(MarkableScanner 관용구 — 역방향
 * 의존 없이 판정만 역주입). UI disabled·서버 409·DB RESTRICT 3층이 이 판정 하나를 공유한다.
 */
public interface BiosSettingTemplateUsageChecker {

    boolean isInUse(Long templateId);
}

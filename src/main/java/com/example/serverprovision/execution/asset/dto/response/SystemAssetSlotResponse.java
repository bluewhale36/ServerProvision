package com.example.serverprovision.execution.asset.dto.response;

import java.time.Instant;

/**
 * 진단 자산 대시보드의 슬롯 1행. 뷰는 엔티티/도메인 enum 을 모른 채 표시 문자열만 받는다
 * (무결성 라벨·배지 클래스는 서비스가 {@code IntegrityStatusView} 로 미리 계산).
 *
 * @param key            슬롯 식별자(enum name — 교체 URL {@code /{slot}/replace} 경로변수)
 * @param label          자산 표시명 (예: "커널 (vmlinuz-lts)")
 * @param category       성격 분류 라벨 (예: "netboot 아티팩트")
 * @param filename       실제 파일/디렉토리명
 * @param layoutLabel    "단일 파일" / "디렉토리"
 * @param present        자산 본체 존재 여부
 * @param replaceable    UI 업로드 교체 대상 여부(E1-I-2-a — 뷰가 교체 폼 / 빌드 스크립트 안내 분기)
 * @param sizeDisplay    사람이 읽는 크기 (예: "208.0 MB") — 부재 시 "—"
 * @param modifiedAt     최종 수정 시각 — 부재 시 null (뷰에서 "—" 처리)
 * @param statusLabel    무결성 상태 표시 문구 (예: "원본 유지" / "자산 없음" / "서빙 비활성")
 * @param badgeClass     상태 배지 CSS 클래스 (n-badge-green/red/orange/gray)
 * @param replaceCadence 교체 주기 안내 (예: "Alpine 버전 업그레이드 시")
 */
public record SystemAssetSlotResponse(
        String key,
        String label,
        String category,
        String filename,
        String layoutLabel,
        boolean present,
        boolean replaceable,
        String sizeDisplay,
        Instant modifiedAt,
        String statusLabel,
        String badgeClass,
        String replaceCadence
) {
}

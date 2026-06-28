package com.example.serverprovision.execution.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 게스트 서버 상세 화면 인라인 수정 — 운영자 입력 4필드. (U1 §D1: 모두 단일 테이블 {@code guest_server})
 * <ul>
 *   <li>name         — 운영자 식별 이름 (UK, nullable)</li>
 *   <li>modelName    — 사내 모델명 (ipmitool 로 하드웨어에 각인할 운영자 부여값)</li>
 *   <li>serialNumber — 사내 시리얼 번호 (UK)</li>
 *   <li>memo         — 자유 메모</li>
 * </ul>
 * 빈 문자열은 서비스에서 null 로 정규화한다(유니크 컬럼의 '' 중복 충돌 방지).
 */
public record UpdateGuestServerRequest(

        @Size(max = 128, message = "이름은 128자 이하여야 합니다.")
        String name,

        @Size(max = 32, message = "사내 모델명은 32자 이하여야 합니다.")
        String modelName,

        @Size(max = 32, message = "사내 시리얼 번호는 32자 이하여야 합니다.")
        String serialNumber,

        @Size(max = 2000, message = "메모는 2000자 이하여야 합니다.")
        String memo
) {
}

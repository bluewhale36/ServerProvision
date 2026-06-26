package com.example.serverprovision.provisioning.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 게스트 서버 상세 화면에서 인라인 수정 가능한 필드 묶음.
 * <ul>
 *   <li>name / memo          → guest_server</li>
 *   <li>productModelName /
 *       productSerialNumber  → guest_server_custom</li>
 * </ul>
 * 빈 문자열은 서비스에서 null 로 정규화한다(유니크 컬럼의 '' 중복 충돌 방지).
 */
public record UpdateGuestServerRequest(

        @Size(max = 128, message = "이름은 128자 이하여야 합니다.")
        String name,

        @Size(max = 2000, message = "메모는 2000자 이하여야 합니다.")
        String memo,

        @Size(max = 7, message = "사내 모델명은 7자 이하여야 합니다.")
        String productModelName,

        @Size(max = 20, message = "사내 시리얼 번호는 20자 이하여야 합니다.")
        String productSerialNumber
) {
}

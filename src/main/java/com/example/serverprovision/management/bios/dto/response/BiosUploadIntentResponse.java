package com.example.serverprovision.management.bios.dto.response;

import java.util.List;

/**
 * BIOS 번들 업로드 Intent 발급 응답.
 * <p>{@code uploadToken} 은 XHR 업로드 요청의 {@code X-Upload-Token} 헤더에 1회용으로 실어야 한다.
 * {@code warnings} 는 하드 거절 사유가 아닌 소프트 경고 (예: 동일 manifestHash 의 활성 BIOS 존재,
 * fileCount 가 이례적으로 많음 등) 로, 클라이언트가 confirm 다이얼로그로 사용자 의사를 확인한다.</p>
 */
public record BiosUploadIntentResponse(
        String uploadToken,
        List<String> warnings
) {
}

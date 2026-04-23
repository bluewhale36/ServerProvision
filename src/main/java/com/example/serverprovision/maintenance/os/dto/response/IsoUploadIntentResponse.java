package com.example.serverprovision.maintenance.os.dto.response;

import java.util.List;

/**
 * Intent 핸드셰이크 성공 응답.
 * {@code uploadToken} 은 이후 XHR 업로드 요청의 {@code X-Upload-Token} 헤더에 실려야 한다.
 * {@code warnings} 는 하드 거절까지는 아니지만 사용자 확인이 필요한 메시지 (예: 이름 유사 파일 존재).
 */
public record IsoUploadIntentResponse(
        String uploadToken,
        List<String> warnings
) {}

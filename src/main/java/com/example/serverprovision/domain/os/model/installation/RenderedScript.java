package com.example.serverprovision.domain.os.model.installation;

/**
 * 도메인 설치 모델이 생성한 자동화 스크립트 결과물.
 *
 * <p>{@link OSInstallation#getInstallScript(InstallationContext)} 의 반환 타입.
 * 서빙 계층은 {@link #format()} 을 기반으로 Content-Type 과 파일명을 결정하고,
 * {@link #content()} 를 응답 본문으로 내보낸다.</p>
 *
 * @param content 완성된 스크립트 텍스트
 * @param format  스크립트 포맷 (Kickstart / Autoinstall / Unattend)
 */
public record RenderedScript(
        String content,
        InstallScriptFormat format
) {
    public RenderedScript {
        if (content == null) {
            throw new IllegalArgumentException("content 는 null 일 수 없습니다.");
        }
        if (format == null) {
            throw new IllegalArgumentException("format 은 null 일 수 없습니다.");
        }
    }
}

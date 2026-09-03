package com.example.serverprovision.execution.engine.windows;

import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * 토큰 번들이 내주는 파일 5종 — 파일명을 enum 으로 매칭하므로 경로 조작({@code ..})이 애초에 성립하지 않는다
 * (문자열로 경로를 이어 붙이지 않는다). 순서는 win.ipxe 가 받는 순서다.
 */
public enum WindowsInstallFile {

    WIMBOOT("wimboot", true, MediaType.APPLICATION_OCTET_STREAM, null),
    WINPESHL("winpeshl.ini", false, MediaType.TEXT_PLAIN, StandardCharsets.US_ASCII),
    INSTALL_BAT("install.bat", false, MediaType.TEXT_PLAIN, StandardCharsets.US_ASCII),
    AUTOUNATTEND("autounattend.xml", false, MediaType.APPLICATION_XML, StandardCharsets.UTF_8),
    BOOT_WIM("boot.wim", true, MediaType.APPLICATION_OCTET_STREAM, null);

    private final String fileName;
    private final boolean streamed;
    private final MediaType mediaType;
    private final Charset charset;

    WindowsInstallFile(String fileName, boolean streamed, MediaType mediaType, Charset charset) {
        this.fileName = fileName;
        this.streamed = streamed;
        this.mediaType = mediaType;
        this.charset = charset;
    }

    public String fileName() {
        return fileName;
    }

    /** 디스크에서 스트리밍하는 파일(wimboot · boot.wim)인가 — 아니면 메모리 렌더본이다. */
    public boolean streamed() {
        return streamed;
    }

    public MediaType mediaType() {
        return charset == null ? mediaType : new MediaType(mediaType, charset);
    }

    public Charset charset() {
        return charset;
    }

    /** URL 마지막 세그먼트 → 파일. 목록 밖 이름은 empty — 컨트롤러가 위조 토큰과 같은 404 로 다룬다. */
    public static Optional<WindowsInstallFile> of(String fileName) {
        return Arrays.stream(values()).filter(f -> f.fileName.equals(fileName)).findFirst();
    }
}

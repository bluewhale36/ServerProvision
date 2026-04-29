package com.example.serverprovision.global.security.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * S3 보안 properties 의 부팅 시 검증. 필수 항목이 누락되면 컨텍스트 시작을 중단하여 운영 사고를 회피한다.
 * <p>특히 {@link PathSecurityProperties#allowedRoots()} 미설정 시 모든 경로 입력이 통과되는 사고가 발생하므로
 * 빈 값이면 즉시 {@link IllegalStateException} 으로 fail-fast.</p>
 *
 * <p><b>배포 환경 가정 (불가침)</b> : 본 어플리케이션은 <b>Rocky Linux 9.x</b> 에 배포된다. 따라서 본 클래스의
 * 경로 정규화 로직은 POSIX 경로 (forward slash separator) 를 전제로 한다. trailing slash 제거에 사용된
 * {@code replaceAll("/+$", "")} 도 Linux-only 가정의 산물이며, Windows 빌드/실행은 지원하지 않는다.</p>
 *
 * @implNote Linux/POSIX 전용. Windows 호환을 위해 {@link java.io.File#separator} 기반 로직으로 일반화하지 않는다.
 *           운영 환경이 Linux 외로 변경될 일이 없다는 전제하에 단순함을 우선한다.
 */
@Slf4j
// proxyBeanMethods=false : 본 클래스는 @Bean 메서드가 없어 CGLIB proxy 가 불필요.
// 다중 생성자(운영 4-arg + 테스트 3-arg) 환경에서 Spring 7 CGLIB 가 default 생성자를 찾으려 시도해
// `No default constructor found` 로 부팅 실패하던 회귀를 차단한다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        PathSecurityProperties.class,
        UploadSecurityProperties.class,
        FileSystemSecurityProperties.class
})
public class SecurityPropertiesValidator {

    private final PathSecurityProperties pathSecurityProperties;
    private final UploadSecurityProperties uploadSecurityProperties;
    private final FileSystemSecurityProperties fileSystemSecurityProperties;
    private final MultipartProperties multipartProperties;

    // 단일 생성자 — Spring 7 CGLIB 가 다중 생성자 환경에서 default 생성자를 요구하던 회귀 회피.
    // 테스트는 4-arg 직접 호출 + multipart 검증 skip 시 null 전달.
    public SecurityPropertiesValidator(
            PathSecurityProperties pathSecurityProperties,
            UploadSecurityProperties uploadSecurityProperties,
            FileSystemSecurityProperties fileSystemSecurityProperties,
            MultipartProperties multipartProperties) {
        this.pathSecurityProperties = pathSecurityProperties;
        this.uploadSecurityProperties = uploadSecurityProperties;
        this.fileSystemSecurityProperties = fileSystemSecurityProperties;
        this.multipartProperties = multipartProperties;
    }

    @PostConstruct
    public void validate() {
        if (pathSecurityProperties.allowedRoots() == null
                || pathSecurityProperties.allowedRoots().isEmpty()
                || pathSecurityProperties.allowedRoots().stream().allMatch(s -> s == null || s.isBlank())) {
            throw new IllegalStateException(
                    "provision.path.allowed-roots 가 설정되지 않았습니다. " +
                            "환경변수 PROVISION_ALLOWED_ROOTS 를 설정해주세요. " +
                            "예: PROVISION_ALLOWED_ROOTS=/opt/iso,/opt/bios,/opt/firmware,/opt/subprogram");
        }

        // S3.1 (C1 + 침투 #3 / #5) — 각 root 의 안전성 검증.
        // S3.2 (K3) — trailing slash 정상 입력 / OS 호환성 보강.
        for (String s : pathSecurityProperties.allowedRoots()) {
            if (s == null || s.isBlank()) continue;
            // K3 — trailing slash 를 정리해 비교 안정성 확보 (e.g. "/opt/iso/" 도 "/opt/iso" 와 동일 취급)
            String trimmed = s.trim().replaceAll("/+$", "");
            if (trimmed.isEmpty()) {
                trimmed = "/"; // root "/" 거절 분기로 위임
            }
            Path raw;
            try {
                raw = Path.of(trimmed);
            } catch (InvalidPathException e) {
                throw new IllegalStateException(
                        "provision.path.allowed-roots 에 올바르지 않은 경로 : " + s + " (" + e.getMessage() + ")");
            }
            // 침투 #3 — 상대경로 거절 (cwd 기준 정규화로 의도 외 root 가 만들어지는 것을 차단)
            if (!raw.isAbsolute()) {
                throw new IllegalStateException(
                        "provision.path.allowed-roots 는 절대경로여야 합니다 : " + s);
            }
            Path normalized = raw.toAbsolutePath().normalize();
            // 침투 #5 — root '/' 거절 (모든 절대경로가 통과되어 가드 전체 무력화)
            if (normalized.getNameCount() == 0) {
                throw new IllegalStateException(
                        "provision.path.allowed-roots 에 root('/') 는 허용되지 않습니다 : " + s);
            }
            // C1 — 정규형이 아닌 경로 거절 (예: /opt/../etc → /etc 로 silent 변형되는 것을 차단)
            // K3 — Path.equals 의 OS dependent 동작 회피 위해 string 비교도 병행.
            if (!raw.equals(normalized) && !raw.toString().equals(normalized.toString())) {
                throw new IllegalStateException(
                        "provision.path.allowed-roots 는 정규화된 경로만 허용합니다 (예: '..' 시그먼트 금지) : " + s);
            }
        }
        if (uploadSecurityProperties.maxFileSize() == null
                || uploadSecurityProperties.maxRequestSize() == null
                || uploadSecurityProperties.maxTreeBytes() == null
                || uploadSecurityProperties.maxZipUncompressedBytes() == null) {
            throw new IllegalStateException("provision.upload.* size 항목이 누락되었습니다.");
        }
        if (uploadSecurityProperties.executableBinaryPolicy() == null
                || uploadSecurityProperties.suspiciousFilenamesPolicy() == null) {
            throw new IllegalStateException("provision.upload.executable-binary-policy / suspicious-filenames-policy 누락.");
        }
        if (fileSystemSecurityProperties.maxEntries() <= 0 || fileSystemSecurityProperties.maxDepth() <= 0) {
            throw new IllegalStateException("provision.browse.max-entries / max-depth 양수 필수.");
        }
        // C8 — Spring multipart cap 이 application 가드보다 크면 1차 차단이 무력화된다.
        // 두 값을 sync 하거나 multipart cap ≤ provision.upload cap 으로 설정해야 한다.
        if (multipartProperties != null) {
            DataSize multipartFileCap = multipartProperties.getMaxFileSize();
            DataSize multipartReqCap = multipartProperties.getMaxRequestSize();
            DataSize uploadFileCap = uploadSecurityProperties.maxFileSize();
            DataSize uploadReqCap = uploadSecurityProperties.maxRequestSize();
            if (multipartFileCap != null && uploadFileCap != null
                    && multipartFileCap.toBytes() > uploadFileCap.toBytes()) {
                throw new IllegalStateException(
                        "spring.servlet.multipart.max-file-size (" + multipartFileCap
                                + ") > provision.upload.max-file-size (" + uploadFileCap
                                + ") — 1차 multipart 가드가 무력화됩니다. 두 값을 sync 해주세요.");
            }
            if (multipartReqCap != null && uploadReqCap != null
                    && multipartReqCap.toBytes() > uploadReqCap.toBytes()) {
                throw new IllegalStateException(
                        "spring.servlet.multipart.max-request-size (" + multipartReqCap
                                + ") > provision.upload.max-request-size (" + uploadReqCap
                                + ") — 1차 multipart 가드가 무력화됩니다. 두 값을 sync 해주세요.");
            }
        }
        log.info("[security] properties OK : allowedRoots={}, maxFileSize={}, executablePolicy={}",
                pathSecurityProperties.allowedRoots(),
                uploadSecurityProperties.maxFileSize(),
                uploadSecurityProperties.executableBinaryPolicy());
    }
}

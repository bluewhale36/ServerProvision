package com.example.serverprovision.execution.wininstall.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Windows 무인 설치의 전역 운영 설정({@code provision.windows-install.*}, 토론 1호 Q7 — 환경변수로 받고 화면은
 * 설정됨 · 미설정만 보인다). 정의서가 고르는 것(설치 이미지 · Administrator 비밀번호)은 여기 없다.
 *
 * <p>진단 · TFTP 의 {@code @ConditionalOnProperty} 빈 부재 패턴을 쓰지 않는 이유: 그 선례는 실행기 빈을 통째로
 * 빼기 위한 것이었고, 여기는 빼야 할 빈이 없으며 "미설정" 자체를 대시보드와 정의서 폼이 보여야 한다.
 * 반쪽 설정(루트만 있고 공유 계정 없음)도 기동을 막지 않는다 — 실행 차단은 E4-1-a-3 의 준비도가 맡는다.</p>
 *
 * @param sourceRoot 설치 소스 루트(Samba 공유의 로컬 경로, 예: /srv/pxe/win2025). 비어 있으면 미설정
 * @param shareUnc   WinPE 가 붙는 UNC(예: \\192.168.1.10\win2025)
 * @param timeZone   응답 파일의 Windows 시간대 식별자 — 기본 Korea Standard Time
 */
@ConfigurationProperties(prefix = "provision.windows-install")
public record WindowsInstallProperties(
        String sourceRoot,
        String shareUnc,
        String shareUser,
        String sharePassword,
        String timeZone,
        ProductKeys productKeys
) {

    public static final String DEFAULT_TIME_ZONE = "Korea Standard Time";

    /** 에디션(install.wim 의 EDITIONID)별 제품 키 — 소스에 있는 에디션만 표시 대상이다. 값은 화면에 내지 않는다. */
    public record ProductKeys(String serverStandard, String serverDatacenter) {

        public Optional<String> forEdition(String editionId) {
            if (editionId == null) {
                return Optional.empty();
            }
            String value = switch (editionId.toLowerCase()) {
                case "serverstandard" -> serverStandard;
                case "serverdatacenter" -> serverDatacenter;
                default -> null;
            };
            return Optional.ofNullable(value).filter(v -> !v.isBlank());
        }
    }

    public boolean configured() {
        return isSet(sourceRoot);
    }

    public Optional<Path> sourceRootPath() {
        return configured() ? Optional.of(Path.of(sourceRoot.trim()).toAbsolutePath().normalize()) : Optional.empty();
    }

    /** 공유 접속 정보 셋(UNC · 계정 · 비밀번호)이 모두 있는가 — install.bat 렌더(E4-1-a-3)의 전제. */
    public boolean shareConfigured() {
        return isSet(shareUnc) && isSet(shareUser) && isSet(sharePassword);
    }

    public String effectiveTimeZone() {
        return isSet(timeZone) ? timeZone.trim() : DEFAULT_TIME_ZONE;
    }

    public ProductKeys productKeysOrEmpty() {
        return productKeys == null ? new ProductKeys(null, null) : productKeys;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}

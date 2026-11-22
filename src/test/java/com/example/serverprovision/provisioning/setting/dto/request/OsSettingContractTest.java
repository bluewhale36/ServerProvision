package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-7 — 스크립트 검증과 OS 후처리 계열 가드(서버 안전망). 폼이 같은 상한 · 문장을 data-* 로 받는다. */
class OsSettingContractTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> messages(Object target) {
        return validator.validate(target).stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("스크립트 CRLF 는 LF 로 받는다")
    void crlfNormalized() {
        assertThat(new OsSettingScriptRequest("a.sh", "a\r\nb\r\n").content()).isEqualTo("a\nb\n");
    }

    @Test
    @DisplayName("UTF-8 바이트 상한 경계 · NUL · 공백만 · 11 개")
    void scriptRules() {
        assertThat(messages(new OsSettingScriptRequest("a.sh", "a".repeat(OsSettingScriptRequest.MAX_BYTES)))).isEmpty();
        assertThat(messages(new OsSettingScriptRequest("a.sh", "a".repeat(OsSettingScriptRequest.MAX_BYTES + 1))))
                .containsExactly("스크립트 파일은 64 KB 이하여야 합니다.");
        assertThat(messages(new OsSettingScriptRequest("한.sh", "한".repeat(OsSettingScriptRequest.MAX_BYTES / 3 + 1))))
                .as("한글은 3 바이트라 글자 수가 아니라 바이트로 잰다").containsExactly("스크립트 파일은 64 KB 이하여야 합니다.");
        assertThat(messages(new OsSettingScriptRequest("bin", "a\0b"))).containsExactly("텍스트 파일만 올릴 수 있습니다.");
        assertThat(messages(new OsSettingScriptRequest("a.sh", "  \n"))).containsExactly("내용이 없는 파일은 올릴 수 없습니다.");
        RHELOSSettingRequest eleven = new RHELOSSettingRequest(1L, "enforcing", List.of(), List.of(),
                Collections.nCopies(11, new OsSettingScriptRequest("a.sh", "true")));
        assertThat(messages(eleven)).containsExactly("스크립트는 10 개까지 넣을 수 있습니다.");
    }

    @Test
    @DisplayName("저장본에 scripts 키가 없으면 빈 목록")
    void missingScripts_empty() {
        assertThat(new RHELOSSettingRequest(1L, "enforcing", null, null, null).getScripts()).isEmpty();
    }

    @Test
    @DisplayName("OS 후처리를 쓸 수 있는 계열은 RHEL 하나")
    void supportedFamilies() {
        assertThat(OSFamily.osSettingSupportedNames()).containsExactly("RHEL_BASED");
        assertThat(OSFamily.WINDOWS.isOsSettingSupported()).isFalse();
        assertThat(OSFamily.DEBIAN_BASED.isOsSettingSupported()).isFalse();
    }

    @Test
    @DisplayName("서비스 이름에 역슬래시(systemd 이스케이프)는 받지 않는다 — kickstart 줄에 실을 수 없다")
    void serviceNameBackslash_rejected() {
        assertThat(messages(new ServiceDirectiveRequest("a\\x2db", null))).containsExactly("서비스 이름에 허용되지 않는 문자가 있습니다.");
        assertThat(messages(new ServiceDirectiveRequest("getty@tty1.service", null))).isEmpty();
    }

    @Test
    @DisplayName("줄 번호용 원문 줄: 빈 줄은 세고 파일 끝 줄바꿈은 세지 않는다")
    void contentLines() {
        assertThat(new OsSettingScriptRequest("a.sh", "a\n\nb\n").contentLines()).containsExactly("a", "", "b");
        assertThat(new OsSettingScriptRequest("a.sh", "a\r\nb").contentLines()).containsExactly("a", "b");
    }
}

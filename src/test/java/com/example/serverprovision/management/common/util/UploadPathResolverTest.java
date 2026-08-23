package com.example.serverprovision.management.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R12-1 — 경로 해석기 단위 테스트. os.util.IsoPathResolver 를 공용화 이동(개명)한 클래스로,
 * ISO · BIOS 두 도메인이 공유하므로 계약을 명시적으로 고정한다.
 */
class UploadPathResolverTest {

    private static final class Violation extends RuntimeException {
        Violation(String message) {
            super(message);
        }
    }

    @Test
    @DisplayName("디렉토리 경로(/ 로 끝남) : 업로드 파일명을 이어 붙인다")
    void directoryPath_appendsFilename() {
        String resolved = UploadPathResolver.resolve("/mnt/firmware/2.03/", "image.RBU", Violation::new);
        assertThat(resolved).isEqualTo("/mnt/firmware/2.03/image.RBU");
    }

    @Test
    @DisplayName("파일 경로 : 그대로 반환 (업로드 파일명 무시)")
    void filePath_returnedAsIs() {
        String resolved = UploadPathResolver.resolve("/mnt/firmware/2.03/image.RBU", "whatever.bin", Violation::new);
        assertThat(resolved).isEqualTo("/mnt/firmware/2.03/image.RBU");
    }

    @Test
    @DisplayName("디렉토리 경로 + 파일명 없음 : 호출측이 지정한 예외 팩토리 발동")
    void directoryPath_withoutFilename_throwsViaFactory() {
        assertThatThrownBy(() -> UploadPathResolver.resolve("/mnt/firmware/2.03/", null, Violation::new))
                .isInstanceOf(Violation.class)
                .hasMessageContaining("/mnt/firmware/2.03/");
        assertThatThrownBy(() -> UploadPathResolver.resolve("/mnt/firmware/2.03/", "  ", Violation::new))
                .isInstanceOf(Violation.class);
    }

    @Test
    @DisplayName("null · blank 경로 : 그대로 반환 (필수 검증은 Bean Validation 소관)")
    void nullOrBlankPath_returnedAsIs() {
        assertThat(UploadPathResolver.resolve(null, "a.RBU", Violation::new)).isNull();
        assertThat(UploadPathResolver.resolve(" ", "a.RBU", Violation::new)).isEqualTo(" ");
    }

    // ==== R12-1 — 업로드 경로의 디렉토리 추론 ====

    private static final List<String> RBU = List.of("rbu");

    @Test
    @DisplayName("resolveForUpload : 끝 슬래시가 없어도 마지막 세그먼트에 점이 없으면 디렉토리로 보고 파일명을 붙인다")
    void resolveForUpload_inferDirectoryWithoutTrailingSlash() {
        assertThat(UploadPathResolver.resolveForUpload("/mnt/firmware/v310", "image.RBU", RBU, Violation::new))
                .isEqualTo("/mnt/firmware/v310/image.RBU");
    }

    @Test
    @DisplayName("resolveForUpload : 허용 확장자를 가진 마지막 세그먼트는 저장할 파일 경로로 본다(저장명 지정 유지)")
    void resolveForUpload_keepsExplicitFileName() {
        assertThat(UploadPathResolver.resolveForUpload("/mnt/firmware/v302/custom.RBU", "image.RBU", RBU, Violation::new))
                .isEqualTo("/mnt/firmware/v302/custom.RBU");
    }

    @Test
    @DisplayName("resolveForUpload : 끝 슬래시 경로는 기존과 동일하게 동작")
    void resolveForUpload_trailingSlashUnchanged() {
        assertThat(UploadPathResolver.resolveForUpload("/mnt/firmware/v301/", "image.RBU", RBU, Violation::new))
                .isEqualTo("/mnt/firmware/v301/image.RBU");
    }

    @Test
    @DisplayName("R12-2 D8 : 버전 번호 디렉토리(…/2.03)는 확장자가 허용 목록 밖이므로 디렉토리로 구제된다")
    void resolveForUpload_versionNumberDirectory_inferred() {
        assertThat(UploadPathResolver.resolveForUpload("/mnt/firmware/2.03", "image.RBU", RBU, Violation::new))
                .isEqualTo("/mnt/firmware/2.03/image.RBU");
    }

    @Test
    @DisplayName("looksLikeDirectory : 허용 목록이 있으면 확장자 기준, 없으면 점 유무 기준")
    void looksLikeDirectory_rules() {
        // 허용 목록이 있는 제조사
        assertThat(UploadPathResolver.looksLikeDirectory("/mnt/firmware/v301/", RBU)).isTrue();
        assertThat(UploadPathResolver.looksLikeDirectory("/mnt/firmware/v310", RBU)).isTrue();
        assertThat(UploadPathResolver.looksLikeDirectory("/mnt/firmware/2.03", RBU)).isTrue();
        assertThat(UploadPathResolver.looksLikeDirectory("/mnt/firmware/image.RBU", RBU)).isFalse();
        assertThat(UploadPathResolver.looksLikeDirectory("/mnt/firmware/image.rbu", RBU)).isFalse();
        // 제약 없는 제조사 — 점 유무만 본다
        assertThat(UploadPathResolver.looksLikeDirectory("/mnt/firmware/2.03", List.of())).isFalse();
        assertThat(UploadPathResolver.looksLikeDirectory("/mnt/firmware/v310", List.of())).isTrue();
        assertThat(UploadPathResolver.looksLikeDirectory(null, RBU)).isFalse();
    }
}

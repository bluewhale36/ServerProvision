package com.example.serverprovision.maintenance.os.service;

import com.example.serverprovision.maintenance.os.exception.ExtractionFailedException;
import com.example.serverprovision.maintenance.os.service.IsoPreparationService.PreparedIsoPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IsoPreparationService.prepare 의 사용자 친화 실패 메시지 검증.
 * 실제 mount/추출은 OS 환경에 의존하므로 테스트 대상에서 제외하고, 경로 계약 검증 구간만 확인한다.
 */
class IsoPreparationServiceTest {

    private final IsoPreparationService service = new IsoPreparationService();

    @Test
    @DisplayName("null/blank 경로는 '지정되지 않았습니다' 메시지로 실패한다")
    void blank_isRejected() {
        assertThatThrownBy(() -> service.prepare(null))
                .isInstanceOf(ExtractionFailedException.class)
                .hasMessageContaining("지정되지 않았습니다");

        assertThatThrownBy(() -> service.prepare("   "))
                .isInstanceOf(ExtractionFailedException.class)
                .hasMessageContaining("지정되지 않았습니다");
    }

    @Test
    @DisplayName("HTTP/HTTPS URL 은 접속 검증 없이 passthrough 된다")
    void httpUrl_passthrough() {
        PreparedIsoPath prepared = service.prepare("https://mirror.example.com/rocky/9.5/dvd/");
        assertThat(prepared.effectivePath()).isEqualTo("https://mirror.example.com/rocky/9.5/dvd/");
    }

    @Test
    @DisplayName("존재하지 않는 경로는 '실재하지 않습니다' 메시지로 실패한다 (과거의 '디렉토리가 아닙니다' 오해 해소)")
    void nonExistentPath_isRejected(@TempDir Path tempDir) {
        Path ghost = tempDir.resolve("rocky/9-6/dvd.iso");
        assertThatThrownBy(() -> service.prepare(ghost.toString()))
                .isInstanceOf(ExtractionFailedException.class)
                .hasMessageContaining("실재하지 않습니다")
                .hasMessageContaining(ghost.toString());
    }

    @Test
    @DisplayName("실재하는 디렉토리는 passthrough 된다 (마운트된 ISO 간주)")
    void existingDirectory_passthrough(@TempDir Path tempDir) {
        PreparedIsoPath prepared = service.prepare(tempDir.toString());
        assertThat(prepared.effectivePath()).isEqualTo(tempDir.toString());
    }

    @Test
    @DisplayName(".iso 가 아닌 일반 파일은 '.iso 파일이 아닙니다' 메시지로 실패한다")
    void nonIsoFile_isRejected(@TempDir Path tempDir) throws Exception {
        Path txt = Files.createFile(tempDir.resolve("note.txt"));
        assertThatThrownBy(() -> service.prepare(txt.toString()))
                .isInstanceOf(ExtractionFailedException.class)
                .hasMessageContaining(".iso 파일이 아닙니다");
    }
}

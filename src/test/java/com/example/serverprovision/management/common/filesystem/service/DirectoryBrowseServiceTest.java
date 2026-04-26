package com.example.serverprovision.management.common.filesystem.service;

import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectoryBrowseServiceTest {

    private final DirectoryBrowseService directoryBrowseService = new DirectoryBrowseService();

    @Test
    @DisplayName("browse : includeFiles=false 면 디렉토리만 반환한다")
    void browse_directoriesOnly(@TempDir Path tmp) throws Exception {
        Files.createDirectory(tmp.resolve("beta"));
        Files.createDirectory(tmp.resolve("alpha"));
        Files.writeString(tmp.resolve("readme.txt"), "x");

        DirectoryListingResponse response =
                directoryBrowseService.browse(new DirectoryBrowseRequest(tmp.toString(), false));

        assertThat(response.path()).isEqualTo(tmp.toString());
        assertThat(response.entries())
                .extracting(DirectoryListingResponse.Entry::name)
                .containsExactly("alpha", "beta");
        assertThat(response.entries())
                .extracting(DirectoryListingResponse.Entry::type)
                .containsOnly("DIR");
    }

    @Test
    @DisplayName("browse : includeFiles=true 면 파일과 크기를 함께 반환한다")
    void browse_includesFiles(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("bundle.zip"), new byte[] {1, 2, 3});
        Files.createDirectory(tmp.resolve("scripts"));

        DirectoryListingResponse response =
                directoryBrowseService.browse(new DirectoryBrowseRequest(tmp.toString(), true));

        assertThat(response.entries())
                .extracting(DirectoryListingResponse.Entry::name)
                .containsExactly("bundle.zip", "scripts");
        assertThat(response.entries().get(0).type()).isEqualTo("FILE");
        assertThat(response.entries().get(0).size()).isEqualTo(3L);
        assertThat(response.entries().get(1).type()).isEqualTo("DIR");
    }

    @Test
    @DisplayName("browse : 존재하지 않는 경로면 404 예외")
    void browse_notFound(@TempDir Path tmp) {
        Path missing = tmp.resolve("missing");

        assertThatThrownBy(() -> directoryBrowseService.browse(new DirectoryBrowseRequest(missing.toString(), false)))
                .isInstanceOf(BrowseTargetNotFoundException.class);
    }

    @Test
    @DisplayName("browse : 파일 경로면 409 예외")
    void browse_filePathConflict(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("firmware.bin");
        Files.writeString(file, "bin");

        assertThatThrownBy(() -> directoryBrowseService.browse(new DirectoryBrowseRequest(file.toString(), true)))
                .isInstanceOf(BrowseTargetNotDirectoryException.class);
    }

    @Test
    @DisplayName("browse : invalid path 면 400 예외")
    void browse_invalidPath() {
        assertThatThrownBy(() -> directoryBrowseService.browse(new DirectoryBrowseRequest("\0bad", false)))
                .isInstanceOf(InvalidBrowsePathException.class);
    }
}

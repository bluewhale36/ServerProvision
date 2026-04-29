package com.example.serverprovision.management.common.filesystem.service;

import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;

class DirectoryBrowseServiceTest {

    /** 테스트는 PathPolicy mock 으로 — 입력 그대로 Path 로 통과시켜 테스트 영향 최소화. */
    private final PathPolicyService pathPolicyService = Mockito.mock(PathPolicyService.class);
    private final FileSystemSecurityProperties browseSecurityProperties = new FileSystemSecurityProperties(2000, 8);
    private final DirectoryBrowseService directoryBrowseService =
            new DirectoryBrowseService(pathPolicyService, browseSecurityProperties);

    {
        Mockito.when(pathPolicyService.assertReadablePath(anyString()))
                .thenAnswer(inv -> {
                    String s = inv.getArgument(0, String.class);
                    try {
                        return Path.of(s).toAbsolutePath().normalize();
                    } catch (java.nio.file.InvalidPathException e) {
                        throw new com.example.serverprovision.global.security.exception.PathTraversalException(
                                "invalid: " + e.getMessage());
                    }
                });
    }

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
    @DisplayName("browse : invalid path (null byte) 면 PathTraversalException (S3)")
    void browse_invalidPath() {
        assertThatThrownBy(() -> directoryBrowseService.browse(new DirectoryBrowseRequest("\0bad", false)))
                .isInstanceOf(com.example.serverprovision.global.security.exception.PathTraversalException.class);
    }
}

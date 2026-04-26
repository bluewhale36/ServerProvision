package com.example.serverprovision.management.common.filesystem.service;

import com.example.serverprovision.management.bios.exception.MarkerConflictException;
import com.example.serverprovision.management.bios.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.os.exception.DirectoryMissingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultTargetDirectoryPolicyServiceTest {

    private final DefaultTargetDirectoryPolicyService service = new DefaultTargetDirectoryPolicyService();

    @Test
    @DisplayName("validateForIntent : parent 없음 + allow=false -> DirectoryMissingException")
    void validateForIntent_parentMissing_disallowed(@TempDir Path tmp) {
        Path target = tmp.resolve("missing").resolve("target");

        assertThatThrownBy(() -> service.validateForIntent(target, false))
                .isInstanceOf(DirectoryMissingException.class);
    }

    @Test
    @DisplayName("prepareForUpload : parent 없음 + allow=true -> 상위 디렉토리 생성 허용")
    void prepareForUpload_parentMissing_allowed(@TempDir Path tmp) {
        Path target = tmp.resolve("missing").resolve("target");

        assertThatCode(() -> service.prepareForUpload(target, true))
                .doesNotThrowAnyException();
        assertThat(Files.isDirectory(target.getParent())).isTrue();
    }

    @Test
    @DisplayName("prepareForUpload : non-empty dir -> TargetDirectoryNotEmptyException")
    void prepareForUpload_nonEmptyDir(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("random.txt"), "x");

        assertThatThrownBy(() -> service.prepareForUpload(target, true))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }

    @Test
    @DisplayName("prepareForUpload : marker 존재 -> MarkerConflictException")
    void prepareForUpload_markerConflict(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("target");
        Files.createDirectories(target);
        Files.writeString(target.resolve(".provision.json"), "{}");

        assertThatThrownBy(() -> service.prepareForUpload(target, true))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("prepareForUpload : .DS_Store only -> 허용")
    void prepareForUpload_dsStoreOnly(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("target");
        Files.createDirectories(target);
        Files.writeString(target.resolve(".DS_Store"), "finder");

        assertThatCode(() -> service.prepareForUpload(target, true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prepareForUpload : 파일 경로 점유 -> TargetDirectoryNotEmptyException")
    void prepareForUpload_fileOccupied(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("firmware.bin");
        Files.writeString(target, "x");

        assertThatThrownBy(() -> service.prepareForUpload(target, true))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }
}

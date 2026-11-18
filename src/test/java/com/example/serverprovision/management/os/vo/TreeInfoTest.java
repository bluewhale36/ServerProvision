package com.example.serverprovision.management.os.vo;

import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-2 D-20 — ISO 루트의 트리 설명서 해석과 등록 OS 대조. */
class TreeInfoTest {

    @TempDir Path root;

    @Test
    @DisplayName("[release] name · version 이 정본, 옛 형식은 [general] family · version · stage2 기본값")
    void parse() {
        TreeInfo modern = TreeInfo.parse(List.of("[release]", "name = Rocky Linux", "version = 9.5",
                "[stage2]", "mainimage = images/install.img")).orElseThrow();
        assertThat(modern.label()).isEqualTo("Rocky Linux 9.5");
        assertThat(modern.stage2()).isEqualTo("images/install.img");

        TreeInfo legacy = TreeInfo.parse(List.of("[general]", "family = Rocky Linux", "version = 8.10")).orElseThrow();
        assertThat(legacy.name()).isEqualTo("Rocky Linux");
        assertThat(legacy.version()).isEqualTo("8.10");
        assertThat(legacy.stage2()).isEqualTo(TreeInfo.DEFAULT_STAGE2);

        assertThat(TreeInfo.parse(List.of("[release]", "name = Rocky Linux"))).isEmpty();
    }

    @Test
    @DisplayName("파일이 없으면 empty, 있으면 읽는다")
    void read() throws Exception {
        assertThat(TreeInfo.read(root)).isEmpty();
        Files.writeString(root.resolve(TreeInfo.FILE_NAME), "[release]\nname = Rocky Linux\nversion = 10.0\n");
        assertThat(TreeInfo.read(root)).map(TreeInfo::label).contains("Rocky Linux 10.0");
    }

    @Test
    @DisplayName("배포판 이름(대소문자 무시)과 주 버전이 같아야 일치 — 세부 버전은 달라도 된다")
    void matches() {
        TreeInfo rocky95 = new TreeInfo("Rocky Linux", "9.5", TreeInfo.DEFAULT_STAGE2);
        assertThat(rocky95.matches(OSName.ROCKY_LINUX, "9.6")).isTrue();
        assertThat(new TreeInfo("rocky linux", "9.5", TreeInfo.DEFAULT_STAGE2).matches(OSName.ROCKY_LINUX, "9")).isTrue();
        assertThat(new TreeInfo("Rocky Linux", "11.0", TreeInfo.DEFAULT_STAGE2).matches(OSName.ROCKY_LINUX, "10.0")).isFalse();
        assertThat(rocky95.matches(OSName.CENTOS, "9.5")).isFalse();
        assertThat(new TreeInfo("Rocky Linux", "beta", TreeInfo.DEFAULT_STAGE2).matches(OSName.ROCKY_LINUX, "9.5")).isFalse();
    }
}

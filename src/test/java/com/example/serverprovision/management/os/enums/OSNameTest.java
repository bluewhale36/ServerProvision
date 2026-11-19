package com.example.serverprovision.management.os.enums;

import com.example.serverprovision.management.os.vo.OsMajorVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** S23-1 — OS 카탈로그 상수의 판정: 지원 여부, 선택지 라벨, 추출 능력, 버전 규칙, 수정 화면 선택지. */
class OSNameTest {

    @Test
    @DisplayName("지원 상수는 등록할 수 있고 미지원 상수 넷은 등록할 수 없다")
    void registrable() {
        assertThat(OSName.registrableValues()).containsExactly(
                OSName.ROCKY_LINUX_8, OSName.ROCKY_LINUX_9, OSName.ROCKY_LINUX_10,
                OSName.RHEL_8, OSName.RHEL_9, OSName.RHEL_10,
                OSName.WINDOWS_SERVER_2025, OSName.UBUNTU);
        assertThat(OSName.ROCKY_LINUX.registrable()).isFalse();
        assertThat(OSName.CENTOS.registrable()).isFalse();
        assertThat(OSName.WINDOWS.registrable()).isFalse();
        assertThat(OSName.WINDOWS_SERVER.registrable()).isFalse();
    }

    @Test
    @DisplayName("라벨은 배포판 이름에 메이저를 붙이고, 배포판 이름(displayName)은 메이저 없이 둔다")
    void labelAndDisplayName() {
        assertThat(OSName.ROCKY_LINUX_9.label()).isEqualTo("Rocky Linux 9");
        assertThat(OSName.ROCKY_LINUX_9.getDisplayName()).isEqualTo("Rocky Linux");
        assertThat(OSName.RHEL_10.label()).isEqualTo("Red Hat Enterprise Linux 10");
        assertThat(OSName.WINDOWS_SERVER_2025.label()).isEqualTo("Windows Server 2025");
        assertThat(OSName.UBUNTU.label()).isEqualTo("Ubuntu");
        assertThat(OSName.CENTOS.label()).isEqualTo("CentOS");
        assertThat(OSName.ROCKY_LINUX_9.getMajor()).isEqualTo(OsMajorVersion.of(9));
        assertThat(OSName.ROCKY_LINUX.getMajor()).isNull();
    }

    @Test
    @DisplayName("추출은 지원하는 RHEL 계열만 — Windows, Ubuntu, 미지원 RHEL 상수는 아니다")
    void extractable() {
        assertThat(OSName.ROCKY_LINUX_9.extractable()).isTrue();
        assertThat(OSName.RHEL_8.extractable()).isTrue();
        assertThat(OSName.WINDOWS_SERVER_2025.extractable()).isFalse();
        assertThat(OSName.UBUNTU.extractable()).isFalse();
        assertThat(OSName.ROCKY_LINUX.extractable()).isFalse();
        assertThat(OSName.CENTOS.extractable()).isFalse();
    }

    @Test
    @DisplayName("버전 규칙: RHEL 계열은 메이저로 시작, Windows Server 2025 는 2025 그대로, Ubuntu 는 자유")
    void acceptsVersion() {
        assertThat(OSName.ROCKY_LINUX_9.acceptsVersion("9.6")).isTrue();
        assertThat(OSName.ROCKY_LINUX_9.acceptsVersion("9")).isTrue();
        assertThat(OSName.ROCKY_LINUX_9.acceptsVersion("10.0")).isFalse();
        assertThat(OSName.ROCKY_LINUX_9.acceptsVersion("9a")).isFalse();
        assertThat(OSName.WINDOWS_SERVER_2025.acceptsVersion("2025")).isTrue();
        assertThat(OSName.WINDOWS_SERVER_2025.acceptsVersion(" 2025 ")).isTrue();
        assertThat(OSName.WINDOWS_SERVER_2025.acceptsVersion("2022")).isFalse();
        assertThat(OSName.WINDOWS_SERVER_2025.acceptsVersion("2025.1")).isFalse();
        assertThat(OSName.UBUNTU.acceptsVersion("24.04")).isTrue();
    }

    @Test
    @DisplayName("수정 화면 선택지: 지금 값이 미지원이면 맨 앞에 남기고, 지원 값이면 지원 상수만")
    void selectableKeeping() {
        assertThat(OSName.selectableKeeping(OSName.CENTOS)).first().isEqualTo(OSName.CENTOS);
        assertThat(OSName.selectableKeeping(OSName.CENTOS)).hasSize(OSName.registrableValues().size() + 1);
        assertThat(OSName.selectableKeeping(OSName.ROCKY_LINUX_9)).isEqualTo(OSName.registrableValues());
        assertThat(OSName.selectableKeeping(null)).isEqualTo(OSName.registrableValues());
    }

    @Test
    @DisplayName("목록 순서는 DB 가 아니라 선언 순서: Rocky 8 < Rocky 10 < RHEL 8 < Windows Server 2025 < 미지원")
    void catalogOrder() {
        var rows = new java.util.ArrayList<>(java.util.List.of(
                com.example.serverprovision.management.os.entity.OSMetadata.builder().osName(OSName.CENTOS).osVersion("7.9").build(),
                com.example.serverprovision.management.os.entity.OSMetadata.builder().osName(OSName.ROCKY_LINUX_10).osVersion("10.0").build(),
                com.example.serverprovision.management.os.entity.OSMetadata.builder().osName(OSName.WINDOWS_SERVER_2025).osVersion("2025").build(),
                com.example.serverprovision.management.os.entity.OSMetadata.builder().osName(OSName.RHEL_8).osVersion("8.8").build(),
                com.example.serverprovision.management.os.entity.OSMetadata.builder().osName(OSName.ROCKY_LINUX_8).osVersion("8.10").build()));
        rows.sort(com.example.serverprovision.management.os.entity.OSMetadata.CATALOG_ORDER);
        assertThat(rows).extracting(com.example.serverprovision.management.os.entity.OSMetadata::getOsName).containsExactly(
                OSName.ROCKY_LINUX_8, OSName.ROCKY_LINUX_10, OSName.RHEL_8, OSName.WINDOWS_SERVER_2025, OSName.CENTOS);
    }
}

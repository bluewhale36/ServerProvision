package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.enums.RhelRelease;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-2 D-21 — OS 등록 선택지: Rocky Linux 는 지원 메이저마다 한 줄, 나머지는 OS 이름 한 줄. */
class OsRegistrationOptionTest {

    @Test
    @DisplayName("Rocky Linux 8 · 9 · 10 세 줄 + CentOS · Ubuntu 한 줄씩, 메이저 줄은 버전 앞자리를 준다")
    void of() {
        List<OsRegistrationOption> options = OsRegistrationOption.of(List.of(OSName.ROCKY_LINUX, OSName.CENTOS, OSName.UBUNTU));
        assertThat(options).extracting(OsRegistrationOption::label)
                .containsExactly("Rocky Linux 8", "Rocky Linux 9", "Rocky Linux 10", "CentOS", "Ubuntu");
        assertThat(options.get(1).release()).isEqualTo(RhelRelease.ROCKY_9);
        assertThat(options.get(1).versionPrefix()).isEqualTo("9.");
        assertThat(options.get(3).release()).isNull();
        assertThat(options.get(3).versionPrefix()).isEmpty();
    }
}

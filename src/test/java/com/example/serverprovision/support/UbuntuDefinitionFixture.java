package com.example.serverprovision.support;

import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UbuntuInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UserRequest;
import com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Ubuntu OS 설치 단계를 실은 정의서 상세. 단계 카드 조각을 쓰는 세 화면(정의서 상세 · 고르기 창 · 그룹 고르기 창)이
 * Ubuntu 정의서로도 그려지는지 확인하는 테스트가 함께 쓴다(HF33).
 */
public final class UbuntuDefinitionFixture {

    public static final String HOSTNAME = "ubuntu-node-01";
    public static final String PACKAGE = "openssh-server";

    private UbuntuDefinitionFixture() {
    }

    public static UbuntuInstallationRequest installation() {
        return new UbuntuInstallationRequest(
                5L, 500L,
                new TimezoneRequest("Asia/Seoul", true),
                List.of(new PartitionRequest("/", FileSystem.EXT4, 0L, SizeUnit.GB, true)),
                List.of(new UserRequest("ops", "user-secret-pw", null, true, false, false)),
                HOSTNAME, List.of(PACKAGE));
    }

    public static SettingDetailResponse detail(long id, String name) {
        return new SettingDetailResponse(id, name, false, true, false, 0L,
                List.of(installation()),
                List.of(), List.of(),
                new ReferenceNamesResponse(Map.of(), Map.of(), Map.of(), Map.of(5L, "Ubuntu"),
                        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()),
                LocalDateTime.now(), LocalDateTime.now());
    }
}

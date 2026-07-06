package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;

/**
 * OS 별 권장 파티션 프리셋 항목. ({@code GET /provisioning/setting/default-partitions})
 * {@code size}/{@code sizeUnit} 이 {@code null} 이면 사용자가 크기를 직접 결정해야 함을 뜻한다.
 */
public record PartitionPresetResponse(
        String mountPoint,
        FileSystem fileSystem,
        Long size,
        SizeUnit sizeUnit,
        boolean isGrow
) {
}

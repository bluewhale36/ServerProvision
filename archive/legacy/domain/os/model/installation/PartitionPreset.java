package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.example.serverprovision.domain.os.model.enums.SizeUnit;

/**
 * OS 설치 시 권장하는 파티션 기본값 프리셋.
 *
 * <p>{@code size} / {@code sizeUnit} 이 {@code null} 이면 크기를 사용자가 직접 입력해야 한다.
 * 이 레코드는 컨트롤러 응답에 직렬화되므로 Jackson 이 열거값 이름 그대로 출력한다.
 * (예: {@code fileSystem} → {@code "EXT4"}, {@code sizeUnit} → {@code "GB"})
 */
public record PartitionPreset(
        String mountPoint,
        FileSystem fileSystem,
        Long size,       // null → 사용자가 크기·grow 를 직접 결정
        SizeUnit sizeUnit,
        boolean isGrow
) {}

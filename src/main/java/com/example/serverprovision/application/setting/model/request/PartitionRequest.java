package com.example.serverprovision.application.setting.model.request;

import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.example.serverprovision.domain.os.model.enums.SizeUnit;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

/**
 * 파티션 설정 정보를 담는 Request DTO이다.
 *
 * <p>역할: {@link OSInstallationRequest#getPartitions()}의 각 항목 타입이다.
 * Kickstart {@code part} 명령의 파라미터에 대응하며, 마운트포인트·파일 시스템·디스크명·
 * 크기·grow 옵션을 포함한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}가
 * 이 DTO를 {@link com.example.serverprovision.domain.os.model.installation.Partition} 도메인
 * 값 객체로 변환할 때, {@link com.example.serverprovision.domain.os.model.enums.SizeUnit#toMB}를
 * 사용하여 입력 단위(MB/GB/TB)를 Kickstart {@code --size} 단위인 MiB로 변환한다.
 * 필수 마운트포인트({@code /}, {@code /boot}, {@code /boot/efi}, {@code swap}) 포함 여부,
 * grow 옵션 중복 여부, 파티션 크기 유효성은 도메인 계층({@link com.example.serverprovision.domain.os.model.installation.LinuxInstallation})에서
 * 검증한다.</p>
 *
 * <p>확장 가이드: 새 파티션 속성(예: RAID 레벨)이 필요하면 이 클래스에 필드를 추가하고
 * {@code @JsonCreator} 생성자에 파라미터를 추가한다. 도메인 값 객체
 * {@link com.example.serverprovision.domain.os.model.installation.Partition}에도 동일하게
 * 반영해야 하며, Resolver의 변환 코드도 함께 수정해야 한다.</p>
 */
@Getter
public class PartitionRequest {

    /**
     * 파티션 마운트포인트이다. Kickstart {@code part} 명령의 첫 번째 인자에 해당한다.
     * {@code swap}, {@code /}, {@code /}로 시작하는 절대 경로만 허용된다.
     */
    @NotBlank(message = "마운트 포인트는 필수 입력값입니다.")
    private final String mountPoint;

    /**
     * 파티션 파일 시스템 유형이다. Kickstart {@code --fstype} 파라미터에 해당한다.
     * 마운트포인트와 파일 시스템의 조합은 도메인 계층에서 검증된다.
     */
    @NotNull(message = "파일 시스템은 필수 값입니다.")
    private final FileSystem fileSystem;

    /**
     * 파티션을 생성할 디스크 장치명이다. Kickstart {@code --ondisk} 파라미터에 해당한다.
     * {@code null}이면 자동 할당 그룹으로 처리되며, grow 옵션 중복 검증 시 동일 그룹으로 묶인다.
     */
    private final String diskName;

    /**
     * 파티션 크기이다. {@code sizeUnit}과 함께 해석되며, Resolver에서 MiB로 변환된다.
     * grow 파티션({@code isGrow=true})은 크기 0이 유효하므로 Bean Validation의 {@code @Positive}를
     * 적용하지 않는다. 크기 유효성은 도메인 계층에서 {@code isGrow}와 함께 검증된다.
     */
    private final long size;

    /**
     * 파티션 크기 단위이다. Kickstart는 MiB 단위를 사용하므로 Resolver에서
     * {@link com.example.serverprovision.domain.os.model.enums.SizeUnit#toMB}로 변환된다.
     */
    @NotNull(message = "크기 단위는 필수 값입니다.")
    private final SizeUnit sizeUnit;

    /**
     * 남은 디스크 공간을 모두 사용하는 grow 옵션 활성화 여부이다.
     * Kickstart {@code --grow} 파라미터에 해당하며, 동일 디스크({@code diskName})에
     * grow 파티션은 하나만 허용된다.
     */
    private final boolean isGrow;

    @JsonCreator
    public PartitionRequest(
            @JsonProperty("mountPoint") String mountPoint,
            @JsonProperty("fileSystem") FileSystem fileSystem,
            @JsonProperty("diskName")   String diskName,
            @JsonProperty("size")       long size,
            @JsonProperty("sizeUnit")   SizeUnit sizeUnit,
            @JsonProperty("isGrow")     boolean isGrow) {
        this.mountPoint = mountPoint;
        this.fileSystem = fileSystem;
        this.diskName   = diskName;
        this.size       = size;
        this.sizeUnit   = sizeUnit;
        this.isGrow     = isGrow;
    }
}

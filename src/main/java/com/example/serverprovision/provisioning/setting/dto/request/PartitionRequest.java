package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * 파티션 설정 항목 (리눅스 스코프 — mountPoint/fileSystem 은 Kickstart {@code part} ·
 * autoinstall {@code storage} 의 표현이다. 필수 마운트포인트 등 OS 계열별 검증은 U2-3 의 책임).
 *
 * <p>파티션이 놓이는 곳은 언제나 <b>OS 영역 볼륨 하나</b>다(U4-1 토론 E8 · E14 — 설치기는 OS 영역만 파티셔닝하고
 * Data 영역은 설치 뒤 OS 후처리가 맡는다). 그래서 구 {@code diskName}(장치명 자유 문자열, U4-1-3 에서 제거)은 없다 —
 * 구 payload 의 그 키는 역직렬화가 미지 속성으로 무시한다. 실제 장치 매핑은 실행(E)의 몫.</p>
 */
@Getter
public class PartitionRequest {

    @NotBlank(message = "마운트 포인트는 필수 입력값입니다.")
    @Pattern(regexp = "^(swap|/[A-Za-z0-9._/-]*)$",
            message = "마운트 포인트는 swap 또는 / 로 시작하는 절대경로만 가능합니다.")
    private final String mountPoint;

    @NotNull(message = "파일 시스템은 필수 값입니다.")
    private final FileSystem fileSystem;

    /** 파티션 크기. {@code isGrow} 파티션은 0 허용. */
    private final long size;

    @NotNull(message = "크기 단위는 필수 값입니다.")
    private final SizeUnit sizeUnit;

    /** 남은 공간을 모두 차지하는 grow 파티션 여부. */
    private final boolean isGrow;

    @JsonCreator
    public PartitionRequest(
            @JsonProperty("mountPoint") String mountPoint,
            @JsonProperty("fileSystem") FileSystem fileSystem,
            // boxed + null-coalesce: Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성 대응 —
            // 누락 시 size=0(grow 전용)·isGrow=false 라는 레거시 의미를 유지한다.
            @JsonProperty("size")       Long size,
            @JsonProperty("sizeUnit")   SizeUnit sizeUnit,
            @JsonProperty("isGrow")     Boolean isGrow
    ) {
        this.mountPoint = mountPoint;
        this.fileSystem = fileSystem;
        this.size       = size != null ? size : 0L;
        this.sizeUnit   = sizeUnit;
        this.isGrow     = isGrow != null && isGrow;
    }

    // 직렬화 키를 wire 계약("isGrow")에 고정 — Jackson 기본 명명은 is-접두를 벗겨버린다.
    @JsonProperty("isGrow")
    public boolean isGrow() {
        return isGrow;
    }

    /** 고정 크기(바이트) — grow 파티션은 0. OS 영역 볼륨 하한 대조(U4-1-3 D7)에 쓴다. */
    @JsonIgnore
    public long fixedBytes() {
        return isGrow || sizeUnit == null ? 0L : sizeUnit.toBytes(size);
    }
}

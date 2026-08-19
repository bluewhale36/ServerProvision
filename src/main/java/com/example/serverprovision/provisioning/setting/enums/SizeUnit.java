package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파티션 크기 단위 (계약측 enum). 실제 1024 기반 이진 단위이므로 표시엔 IEC 표기(MiB/GiB/TiB)를 쓴다.
 * MiB 환산 계수는 실행 시 Kickstart {@code --size}(MiB) 변환에 쓰일 값이나, 변환 로직 자체는
 * 실행 도메인 편입 슬라이스의 책임이다.
 */
@RequiredArgsConstructor
@Getter
public enum SizeUnit {

    MB(1L, "MiB"),
    GB(1_024L, "GiB"),
    TB(1_024L * 1_024L, "TiB");

    private final long toMBFactor;
    private final String symbol;

    /** MiB × 2²⁰ — 디스크 용량(십진 {@link DiskCapacityUnit})과 바이트로 맞춰 비교한다(U4-1-3 D7). */
    public long toBytes(long size) {
        return size * toMBFactor * 1_048_576L;
    }
}

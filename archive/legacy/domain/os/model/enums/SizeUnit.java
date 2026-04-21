package com.example.serverprovision.domain.os.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파티션 크기 단위.
 *
 * <p>Rocky Linux Kickstart 의 {@code part --size=} 는 MiB(= MB) 단위를 사용한다.
 * {@link #toMB(long)} 를 통해 입력값을 MiB 으로 변환하면 Kickstart 스크립트에
 * 직접 삽입할 수 있는 값이 된다.
 */
@Getter
@RequiredArgsConstructor
public enum SizeUnit {

    MB(1L,              "MiB"),
    GB(1_024L,          "GiB"),
    TB(1_024L * 1_024L, "TiB");

    /** Kickstart {@code --size}(MiB) 로의 변환 계수 */
    private final long toMBFactor;

    /** 사용자에게 노출되는 단위 기호. 실제 1024 기반 이진 단위이므로 IEC 표기(MiB/GiB/TiB)를 사용한다. */
    private final String symbol;

    /**
     * 주어진 값을 MiB 단위로 변환한다.
     *
     * <p>음수는 허용하지 않는다. grow 전용 파티션의 경우 size=0 을 전달하면 0 을 그대로 반환한다.
     * overflow 방지를 위해 {@link Math#multiplyExact}를 사용한다.
     *
     * @param value 입력 크기 (이 단위 기준, 0 이상)
     * @return MiB 단위 환산값 (value=0이면 0 반환 — grow 파티션 전용)
     * @throws IllegalArgumentException value 가 음수인 경우
     * @throws ArithmeticException      변환 결과가 long 범위를 초과하는 경우
     */
    public long toMB(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("크기는 음수일 수 없습니다: " + value);
        }
        if (value == 0) return 0L;  // grow 전용 파티션: size=0 → --size 생략 대상
        return Math.multiplyExact(value, toMBFactor);
    }
}

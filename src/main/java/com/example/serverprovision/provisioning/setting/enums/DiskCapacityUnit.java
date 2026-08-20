package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 디스크 용량 단위 — 판매 표기의 십진 단위(480GB · 4TB) 다 (U4-1-1 D2).
 *
 * <p>파티션 크기의 {@link SizeUnit} 은 이진(GiB · TiB) 이라 재사용하지 않는다. 같은 글자 GB 가 파티션에서는
 * 1024 기반, 디스크에서는 1000 기반이라 한 enum 에 두면 표시가 어긋난다. 480GB SSD 가 lsblk 에서
 * 447G 로 보이는 허용 오차는 실행(E)의 매칭이 다룬다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum DiskCapacityUnit {
    GB("GB", 1_000_000_000L),
    TB("TB", 1_000_000_000_000L);

    private final String symbol;
    /** 십진 바이트 수 — 파티션(이진 {@link SizeUnit})과 바이트로 맞춰 비교한다(U4-1-3 D7). */
    private final long bytes;

    public long toBytes(long size) {
        return size * bytes;
    }
}

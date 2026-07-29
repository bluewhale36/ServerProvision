package com.example.serverprovision.global.asset;

import java.nio.file.Path;
import java.util.Optional;

/**
 * {@link AtomicAssetSwap#swap} 의 결과 — 스왑 직전 archive 된 이전본의 버전 id 와 그 바이트 경로. 최초 적용
 * (target 부재)에서는 archive 할 이전본이 없어 둘 다 {@link Optional#empty()} 다.
 *
 * @param archivedVersionId 스왑 직전 archive 된 이전본 버전 id(최초 적용이면 empty)
 * @param archivedPath      그 이전본 바이트의 이력 경로(복원 소스, 최초 적용이면 empty)
 */
public record SwapResult(Optional<Long> archivedVersionId, Optional<Path> archivedPath) {
}

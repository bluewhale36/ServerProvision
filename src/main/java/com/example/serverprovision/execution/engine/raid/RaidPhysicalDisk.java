package com.example.serverprovision.execution.engine.raid;

/**
 * 카드 뒤 물리 디스크 1대(E3.5-1) — 진단 수집(lsblk)이 못 보는 것을 계열 CLI 가 보고한다(OPEN-1 해소).
 *
 * @param slot 계열 원문 표기 그대로 — MegaRAID {@code EID:Slt}(252:0) · IR {@code Encl:Bay}(1:0)
 * @param size CLI 원문 표기 그대로(예: {@code 446.625 GB} · {@code 3815447 MB}) — 바이트 환산과 표기
 *             용량 계급 스냅은 매칭(E3.5-2)의 몫이라 여기서 해석하지 않는다(원문 보존)
 * @param volumeRef 소속 볼륨 참조(MegaRAID DG 번호 · IR 볼륨 ID) — 미소속(패스스루 후보)은 null
 */
public record RaidPhysicalDisk(
        String slot,
        String type,
        String transport,
        String size,
        String state,
        String model,
        String serial,
        String volumeRef
) {
}

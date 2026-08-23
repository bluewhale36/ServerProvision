package com.example.serverprovision.management.bmc.firmware;

import com.example.serverprovision.management.common.firmware.FirmwareFilePolicyStrategy;

/**
 * R12-2 — BMC 펌웨어 파일명 정책의 자원 마커.
 *
 * <p>검사 알고리즘과 데이터 계약은 공용 {@link FirmwareFilePolicyStrategy} 가 갖고, 이 인터페이스는
 * <b>"BMC 자원의 정책"이라는 갈래만 표시</b>한다. 같은 제조사라도 BIOS 와 BMC 는 요구 형식이 다르다
 * (GIGABYTE 기준 BIOS 는 {@code .RBU}, BMC 는 {@code .ima_enc}) — 그래서 자원별로 전략을 나눈다.</p>
 */
public interface BmcFirmwareFilePolicyStrategy extends FirmwareFilePolicyStrategy {
}

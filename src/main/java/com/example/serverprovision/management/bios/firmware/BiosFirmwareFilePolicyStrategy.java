package com.example.serverprovision.management.bios.firmware;

import com.example.serverprovision.management.common.firmware.FirmwareFilePolicyStrategy;

/**
 * R12-2 — BIOS 펌웨어 파일명 정책의 자원 마커.
 *
 * <p>검사 알고리즘과 데이터 계약은 공용 {@link FirmwareFilePolicyStrategy} 가 갖고, 이 인터페이스는
 * <b>"BIOS 자원의 정책"이라는 갈래만 표시</b>한다. Spring 이 이 타입으로 목록을 주입하므로 BMC 전략이
 * BIOS 조회에 섞이지 않는다 — 자원 구분을 {@code supports} 인자로 넘기는 방식은 검사 누락이 조용한
 * 오작동으로 이어지므로 타입으로 갈랐다(R12-2 D1).</p>
 */
public interface BiosFirmwareFilePolicyStrategy extends FirmwareFilePolicyStrategy {
}

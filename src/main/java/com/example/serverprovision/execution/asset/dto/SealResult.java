package com.example.serverprovision.execution.asset.dto;

/**
 * 무결성 봉인 결과. 존재하는 슬롯만 기록되고 부재 슬롯은 건너뛴다.
 *
 * @param sealed  마커를 기록/갱신한 슬롯 수
 * @param skipped 자산 부재로 건너뛴 슬롯 수
 */
public record SealResult(int sealed, int skipped) {
}

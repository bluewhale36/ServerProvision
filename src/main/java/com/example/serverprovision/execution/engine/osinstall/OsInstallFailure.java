package com.example.serverprovision.execution.engine.osinstall;

/** 모든 계열이 함께 쓰는 실패 사유(E4-1-a-3 · E4-1-a-4 의 원장 사유를 E4-1-b-4-2 에서 타입으로 옮김). 화면 문구는 사유 코드로 고른다. */
public enum OsInstallFailure implements OsInstallFailureReason {

    /** 서빙 시각부터 설치 시한이 지났다(재진입 판정 또는 스윕). */
    INSTALL_TIMEOUT,
    /** 재진입 횟수가 상한을 넘었다 — 재PXE 루프. */
    REPXE_LOOP,
    /** 운영자 수동 실패 전환이 열린 서빙 행을 닫았다. */
    OPERATOR,
    /** 새 서빙이 시작될 때 아직 열려 있던 옛 행을 닫았다. */
    SUPERSEDED;

    @Override
    public String code() {
        return name();
    }
}

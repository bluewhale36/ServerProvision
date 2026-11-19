package com.example.serverprovision.execution.installtree.enums;

/**
 * ISO 하나의 설치 작업 트리 상태(메모리). 재기동하면 전부 {@link #ABSENT} 로 돌아간다 — 기동 정리가 디스크도 비운다.
 */
public enum TreeState {
    /** 트리가 없다. 다음 확보 요청이 추출을 시작한다. */
    ABSENT,
    /** 추출 중이다. 확보 요청은 보유자만 더한다. */
    PREPARING,
    /** 트리가 게시되어 서빙할 수 있다. */
    READY,
    /** 추출이 실패했다. 대기하던 보유자에게 한 번 실패를 알리고, 그 뒤의 새 확보 요청은 다시 추출한다. */
    FAILED
}

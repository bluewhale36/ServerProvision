package com.example.serverprovision.global.history.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * 버전 아카이브의 IO 실패(이력 루트 복사 / 해시·크기 계산 실패). {@code @ResponseStatus} 없는
 * {@link DomainException} 이라 advice 가 500 으로 매핑한다({@code MarkerWriteFailedException} 선례).
 * 이 예외가 나면 교체는 스왑 전에 중단된다 — 옛 버전을 보존하지 못한 채 덮어쓰지 않는다.
 */
public class AssetVersionArchiveFailedException extends DomainException {

    public AssetVersionArchiveFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

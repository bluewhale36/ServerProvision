package com.example.serverprovision.execution.asset.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * {@code $OEM$} 페이로드 조립을 시작할 수 없는 상태 — 설치 소스 미설정 · {@code sources} 디렉토리 부재 · 쓰기 권한 없음(E4-1-a-4 §7).
 * 정상 흐름은 대시보드가 같은 판정({@code WindowsOemPayloadAssembler.blockReason})으로 버튼을 disabled + tooltip 하므로
 * 이 예외는 direct POST 안전망이다. base {@link ConflictException} 이 409 로 매핑한다. 조립 도중의 IO 실패는 이 예외가
 * 아니라 {@code UncheckedIOException}(500) — 상태 충돌이 아니라 프로그램 예외다.
 */
public class WindowsOemAssemblyRejectedException extends ConflictException {

    private WindowsOemAssemblyRejectedException(String message) {
        super(message);
    }

    public static WindowsOemAssemblyRejectedException of(String reason) {
        return new WindowsOemAssemblyRejectedException("드라이버 페이로드를 조립할 수 없습니다 — " + reason);
    }
}

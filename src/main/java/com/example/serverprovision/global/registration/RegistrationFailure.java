package com.example.serverprovision.global.registration;

/**
 * 자원 등록 후처리 실패가 자신의 "처분(disposition)" 을 스스로 선언하는 계약.
 *
 * <p>R1-4-4 — 등록 후처리 실행기(예: {@code IsoRegistrationRunner}) 의 실패-종류별 multi-catch
 * (실패 케이스가 늘 때마다 자라는 분기) 를 제거하기 위한 다형성 seam. 등록 실패 예외가 본 인터페이스를
 * 구현하면, 실행기는 {@code catch (RegistrationFailure f)} 한 곳에서 {@link FailureDisposition} 로 분기한다.
 * 새 실패 예외는 정의 시점에 처분을 선언해야 하므로(컴파일 강제) 분류 누락에 의한 silent 오분류가 구조적으로 불가능하다.</p>
 *
 * <p>도메인 무관 — ISO / BIOS / BMC / Subprogram 등록 실패가 공유한다(R1-4-5/6 에서 후속 도메인 onboarding).
 * 단, Spring 의 {@code DataIntegrityViolationException} 같은 외부 클래스는 본 인터페이스를 구현할 수 없어,
 * 실행기의 단일 프레임워크-경계 어댑터 catch 가 처분(QUARANTINE)을 부여한다 — 유일하게 허용되는 잔존 분기.</p>
 */
public interface RegistrationFailure {

	/** 이 실패가 받아야 할 처분. */
	FailureDisposition disposition();
}

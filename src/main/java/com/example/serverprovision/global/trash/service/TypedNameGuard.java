package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.Markable;

/**
 * R7-2 — 이미 로딩된 자원 엔티티에 대한 typed-name 검증.
 *
 * <p>{@link TypedNameVerifier} 와 역할이 갈린다 :</p>
 * <ul>
 *   <li>{@link TypedNameVerifier} (빈) — (resourceType, id) 만 가진 호출부(nudge 교체 / 모달 컨트롤러)가
 *       scanner 로 자원을 <b>조회한 뒤</b> 검증. id → 엔티티 lookup 이 필요해 빈으로 주입.</li>
 *   <li>{@code TypedNameGuard} (static) — 엔티티를 <b>이미 손에 든</b> 호출부(각 도메인의
 *       {@code purgeWithTypedNameCheck} 는 직전에 자원을 로딩함)가 재조회 없이 검증. 의존성이 0 이라 어떤
 *       빈도 주입하지 않으며, 그래서 도메인 service 가 본 가드를 써도 {@code service → TypedNameVerifier → scanner → service}
 *       순환이 형성되지 않는다(R7-2 가 OS/Board/Iso scanner 의 {@code ObjectProvider} 를 제거하는 근거).</li>
 * </ul>
 *
 * <p>검증식 = {@link Markable#displayName()} 와 입력의 정확 일치. 6 도메인이 동일 패턴이라 본 static 메서드로
 * 응집(이전에는 4 도메인이 verifier 위임, 3 도메인이 inline 복붙으로 갈려 있었다).</p>
 */
public final class TypedNameGuard {

	private TypedNameGuard() {
	}

	/**
	 * @throws TypedNameMismatchException 입력 typedName 이 자원의 displayName 과 불일치
	 */
	public static void verify(Markable resource, String typedName) {
		String expected = resource.displayName();
		if (!expected.equals(typedName)) {
			throw new TypedNameMismatchException(expected, typedName);
		}
	}
}

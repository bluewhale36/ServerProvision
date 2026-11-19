package com.example.serverprovision.convention;

/**
 * 규약 위반 한 건. 기준선과는 규칙 · 파일 · 멤버로 만든 키로 맞춘다.
 * 키에 줄 번호를 넣지 않으므로 코드가 위아래로 움직여도 키는 그대로다.
 *
 * @param file   소스 루트(src/main/java) 기준 경로
 * @param member 타입은 Outer.Inner, 메서드는 Type#name(Param), 생성자는 Type#&lt;init&gt;(Param), 필드는 Type#name
 * @param detail 메시지에만 쓰는 보충 사실(줄 수, 의존 대상 등). 키에 들지 않는다
 */
record ConventionViolation(ConventionRule rule, String file, String member, String detail) {

	ConventionViolation(ConventionRule rule, String file, String member) {
		this(rule, file, member, "");
	}

	String key() {
		return rule.name() + "\t" + file + "\t" + member;
	}
}

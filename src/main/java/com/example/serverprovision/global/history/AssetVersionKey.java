package com.example.serverprovision.global.history;

/**
 * 파일 버전 이력의 도메인 무관 식별자(E1-I-2-b-1). {@code (category, name)} 로 한 자산의 버전 계열을
 * 가리킨다 — 진단 자산은 {@code ("DIAGNOSTIC", "modloop-lts")}, 나중 PXE 인프라는 {@code ("PXE", "dhcpd.conf")}.
 *
 * <p>{@code global.trash.ResourceKey} 와 같은 <b>최상위 record</b> 로 둔다 — Hibernate JPQL 의
 * {@code new <fqcn>(...)} 생성자 투영이 중첩 record 로는 해석되지 않기 때문이다(그 선례를 따른다).</p>
 */
public record AssetVersionKey(String category, String name) {
}

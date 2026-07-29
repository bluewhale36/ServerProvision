package com.example.serverprovision.execution.pxeinfra.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PXE 네트워크 구성 요청의 클래스 레벨 교차 검증. 개별 필드 형식(빈 값·양수 등)은 필드 어노테이션이 맡고,
 * 이 제약은 여러 필드를 함께 봐야 하는 의미 검증(CIDR·IP 파싱, 리스풀이 서브넷에 듦, max ≥ default)을 맡는다.
 *
 * <p>판정은 {@link PxeNetworkConfigValidator} 가 VO 술어({@code SubnetCidr.containsRange} 등)를 재사용해
 * 수행한다 — 엔티티 정적 팩토리의 안전망 술어와 동일해 두 곳이 갈라지지 않는다(SSOT).</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PxeNetworkConfigValidator.class)
public @interface ValidPxeNetworkConfig {

    String message() default "PXE 네트워크 구성이 유효하지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package com.example.serverprovision.management.os.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청의 OS 가 새로 고를 수 있는 지원 상수인지 검사한다(S23-1). 미지원 상수는 화면 선택지에 나오지 않으므로
 * 직접 보낸 요청에서만 걸린다. 값이 비어 있으면 검사하지 않는다(OS 무관 자원).
 */
@Documented
@Constraint(validatedBy = RegistrableOsNameValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RegistrableOsName {

	String message() default "지원하지 않는 OS 입니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}

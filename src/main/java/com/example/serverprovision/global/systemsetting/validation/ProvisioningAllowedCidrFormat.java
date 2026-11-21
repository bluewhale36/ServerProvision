package com.example.serverprovision.global.systemsetting.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 프로비저닝 허용 대역 입력 검증. 빈 값도 이 검증이 다룬다(문구 통일). */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProvisioningAllowedCidrFormatValidator.class)
public @interface ProvisioningAllowedCidrFormat {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

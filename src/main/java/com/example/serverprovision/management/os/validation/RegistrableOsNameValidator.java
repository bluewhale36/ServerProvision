package com.example.serverprovision.management.os.validation;

import com.example.serverprovision.management.os.enums.OSName;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link RegistrableOsName} 판정기. 판정은 {@link OSName#registrable()} 하나다(화면 선택지와 같은 기준). */
public class RegistrableOsNameValidator implements ConstraintValidator<RegistrableOsName, OSName> {

	@Override
	public boolean isValid(OSName value, ConstraintValidatorContext context) {
		return value == null || value.registrable();
	}
}

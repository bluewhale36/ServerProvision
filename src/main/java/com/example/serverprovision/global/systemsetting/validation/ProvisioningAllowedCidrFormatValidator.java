package com.example.serverprovision.global.systemsetting.validation;

import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link ProvisioningAllowedCidr#violationOf} 의 첫 위반을 필드 오류 문구로 바꾼다. */
public class ProvisioningAllowedCidrFormatValidator
        implements ConstraintValidator<ProvisioningAllowedCidrFormat, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        return ProvisioningAllowedCidr.violationOf(value)
                .map(violation -> {
                    ctx.disableDefaultConstraintViolation();
                    ctx.buildConstraintViolationWithTemplate(escape(violation.message())).addConstraintViolation();
                    return false;
                })
                .orElse(true);
    }

    /** 메시지 보간기가 '{' · '$' 를 해석하지 않게. */
    private static String escape(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}

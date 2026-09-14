package com.example.serverprovision.global.security.springsecurity.converter;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, String> {

	@Override
	public String convertToDatabaseColumn(Role attribute) {
		return attribute.getRoleName();
	}

	@Override
	public Role convertToEntityAttribute(String dbData) {
		return Arrays.stream(Role.values()).filter(r -> r.getRoleName().equals(dbData)).findFirst().orElse(null);
	}
}

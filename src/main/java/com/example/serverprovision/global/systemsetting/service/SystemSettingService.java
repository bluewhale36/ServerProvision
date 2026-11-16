package com.example.serverprovision.global.systemsetting.service;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.systemsetting.dto.response.SystemSettingResponse;
import com.example.serverprovision.global.systemsetting.entity.SystemSetting;
import com.example.serverprovision.global.systemsetting.enums.SystemSettingKey;
import com.example.serverprovision.global.systemsetting.exception.SystemSettingAccessDeniedException;
import com.example.serverprovision.global.systemsetting.repository.SystemSettingRepository;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 시스템 설정 읽기 · 저장. 행이 없는 키는 키의 기본 최소 권한으로 다룬다. */
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository repository;

    /** 이 권한으로 볼 수 있는 설정 카드(키 선언 순). */
    @Transactional(readOnly = true)
    public List<SystemSettingResponse> accessibleSettings(Collection<Role> roles) {
        Map<SystemSettingKey, SystemSetting> rows = repository.findAll().stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, Function.identity()));
        return Arrays.stream(SystemSettingKey.values())
                .filter(key -> SystemSetting.isAccessible(minRoleOf(key, rows.get(key)), roles))
                .map(key -> toResponse(key, rows.get(key)))
                .toList();
    }

    /** 저장 가드. 컨트롤러가 입력 검증 결과를 보기 전에 부른다(권한 미달이면 값과 무관하게 403). */
    @Transactional(readOnly = true)
    public void assertAccessible(SystemSettingKey key, Collection<Role> roles) {
        Role minRole = minRoleOf(key, repository.findBySettingKey(key).orElse(null));
        if (!SystemSetting.isAccessible(minRole, roles)) {
            throw new SystemSettingAccessDeniedException();
        }
    }

    @Transactional
    public void saveProvisioningAllowedRange(ProvisioningAllowedCidr cidr, Collection<Role> roles) {
        SystemSettingKey key = SystemSettingKey.PROVISIONING_ALLOWED_RANGE;
        SystemSetting setting = repository.findBySettingKey(key).orElseGet(() -> SystemSetting.create(key));
        if (!setting.isAccessibleBy(roles)) {
            throw new SystemSettingAccessDeniedException();
        }
        setting.changeValue(new ProvisioningAllowedRange(cidr));
        repository.save(setting);
    }

    /** 개시 판정용 내부 읽기. 권한 판정을 거치지 않는다. */
    @Transactional(readOnly = true)
    public Optional<ProvisioningAllowedCidr> provisioningAllowedRange() {
        return repository.findBySettingKey(SystemSettingKey.PROVISIONING_ALLOWED_RANGE)
                .map(SystemSetting::getValue)
                .filter(ProvisioningAllowedRange.class::isInstance)
                .map(value -> ((ProvisioningAllowedRange) value).cidr());
    }

    private static Role minRoleOf(SystemSettingKey key, SystemSetting row) {
        return row == null ? key.defaultMinRole() : row.getMinRole();
    }

    private static SystemSettingResponse toResponse(SystemSettingKey key, SystemSetting row) {
        String value = (row == null || row.getValue() == null) ? null : row.getValue().display();
        return new SystemSettingResponse(key, key.label(), value,
                minRoleOf(key, row), row == null ? null : row.getUpdatedAt());
    }
}

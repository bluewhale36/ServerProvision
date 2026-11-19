package com.example.serverprovision.global.systemsetting.service;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.systemsetting.dto.response.SystemSettingResponse;
import com.example.serverprovision.global.systemsetting.entity.SystemSetting;
import com.example.serverprovision.global.systemsetting.enums.SystemSettingKey;
import com.example.serverprovision.global.systemsetting.exception.SystemSettingAccessDeniedException;
import com.example.serverprovision.global.systemsetting.repository.SystemSettingRepository;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    @Mock SystemSettingRepository repository;
    @InjectMocks SystemSettingService service;

    private static final ProvisioningAllowedCidr RANGE = ProvisioningAllowedCidr.parse("10.1.1.0/24");
    private static final SystemSettingKey KEY = SystemSettingKey.PROVISIONING_ALLOWED_RANGE;

    private static SystemSetting stored() {
        SystemSetting s = SystemSetting.create(KEY);
        s.changeValue(new ProvisioningAllowedRange(RANGE));
        return s;
    }

    @Test
    @DisplayName("화면: SUPER_ADMIN 은 카드를 받고 ADMIN · USER 는 빈 목록(행이 없으면 키의 기본 최소 권한)")
    void accessibleSettings() {
        given(repository.findAll()).willReturn(List.of());
        List<SystemSettingResponse> admin = service.accessibleSettings(Set.of(Role.SUPER_ADMIN));
        assertThat(admin).singleElement().satisfies(r -> {
            assertThat(r.key()).isEqualTo(KEY);
            assertThat(r.value()).isNull();
            assertThat(r.minRole()).isEqualTo(Role.SUPER_ADMIN);
        });
        assertThat(service.accessibleSettings(Set.of(Role.ADMIN, Role.USER))).isEmpty();
        assertThat(service.accessibleSettings(Set.of(Role.USER))).isEmpty();
    }

    @Test
    @DisplayName("화면: 저장된 값을 표시 문자열로")
    void accessibleSettingsWithValue() {
        given(repository.findAll()).willReturn(List.of(stored()));
        assertThat(service.accessibleSettings(Set.of(Role.SUPER_ADMIN)).getFirst().value()).isEqualTo("10.1.1.0/24");
    }

    @Test
    @DisplayName("저장: 행이 없으면 기본 최소 권한으로 만든다")
    void saveCreates() {
        given(repository.findBySettingKey(KEY)).willReturn(Optional.empty());
        service.saveProvisioningAllowedRange(RANGE, Set.of(Role.SUPER_ADMIN));
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMinRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(captor.getValue().getValue().display()).isEqualTo("10.1.1.0/24");
    }

    @Test
    @DisplayName("저장: 권한 미달이면 403 예외 · 저장 안 함")
    void saveDenied() {
        given(repository.findBySettingKey(KEY)).willReturn(Optional.of(stored()));
        assertThatThrownBy(() -> service.saveProvisioningAllowedRange(RANGE, Set.of(Role.ADMIN, Role.USER)))
                .isInstanceOf(SystemSettingAccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("권한 가드: ADMIN 거절 · SUPER_ADMIN 통과")
    void assertAccessible() {
        given(repository.findBySettingKey(KEY)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.assertAccessible(KEY, Set.of(Role.ADMIN, Role.USER)))
                .isInstanceOf(SystemSettingAccessDeniedException.class);
        service.assertAccessible(KEY, Set.of(Role.SUPER_ADMIN));
    }

    @Test
    @DisplayName("내부 읽기: 값 있음 · 행 없음")
    void provisioningAllowedRange() {
        given(repository.findBySettingKey(KEY)).willReturn(Optional.of(stored()));
        assertThat(service.provisioningAllowedRange()).contains(RANGE);
        given(repository.findBySettingKey(KEY)).willReturn(Optional.empty());
        assertThat(service.provisioningAllowedRange()).isEmpty();
    }
}

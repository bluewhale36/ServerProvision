package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.provisioning.setting.dto.request.RHELOSSettingRequest;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * U2-3-1 CP4 — OS 후처리 1단 검사기: 공통 가드 + 계열 위임(현재 RHEL family 는 no-op).
 */
@ExtendWith(MockitoExtension.class)
class OSSettingReferenceInspectorTest {

    private static final com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext CTX =
            new com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext(java.util.List.of());

    @Mock OSMetadataRepository osMetadataRepository;

    private OSSettingReferenceInspector build() {
        return new OSSettingReferenceInspector(
                new OsMetadataReferenceChecker(osMetadataRepository),
                List.of(new RHELOSSettingFamilyInspector()));
    }

    @Test
    @DisplayName("공통 — disabled OS 409 · deprecated 서술")
    void commonGuardAndDescribe() {
        OSSettingReferenceInspector inspector = build();
        OSMetadata disabled = Mockito.mock(OSMetadata.class);
        given(disabled.isEnabled()).willReturn(false);
        Mockito.lenient().when(disabled.getOsName()).thenReturn(OSName.ROCKY_LINUX);
        Mockito.lenient().when(disabled.getOsVersion()).thenReturn("8.10");
        given(osMetadataRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(disabled));

        assertThatThrownBy(() -> inspector.validateReferences(
                new RHELOSSettingRequest(2L, "enforcing", List.of(), List.of()), CTX))
                .isInstanceOf(DisabledResourceReferenceException.class);

        OSMetadata deprecated = Mockito.mock(OSMetadata.class);
        given(deprecated.isDeprecated()).willReturn(true);
        given(deprecated.getOsName()).willReturn(OSName.ROCKY_LINUX);
        given(deprecated.getOsVersion()).willReturn("8.10");
        given(osMetadataRepository.findByIdAndIsDeletedFalse(3L)).willReturn(Optional.of(deprecated));
        assertThat(inspector.describeDeprecatedReferences(
                new RHELOSSettingRequest(3L, "enforcing", List.of(), List.of())))
                .containsExactly("Rocky Linux 8.10");
    }
}

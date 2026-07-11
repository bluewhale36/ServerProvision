package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UbuntuInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * U2-3-1 CP4 — OS 설치 1단 검사기: 공통(osMetadata) 가드 + OSFamily 2단 맵 위임.
 */
@ExtendWith(MockitoExtension.class)
class OSInstallationReferenceInspectorTest {

    private static final com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext CTX =
            new com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext(java.util.List.of());

    @Mock OSMetadataRepository osMetadataRepository;
    @Mock com.example.serverprovision.management.os.repository.ISORepository isoRepository;
    @Mock RHELInstallationFamilyInspector rhelFamily;

    private OSInstallationReferenceInspector inspector;

    private OSInstallationReferenceInspector build() {
        given(rhelFamily.family()).willReturn(com.example.serverprovision.provisioning.setting.enums.OSFamily.RHEL_BASED);
        // Debian family 는 의도적으로 미등록 — 2단 맵 미스 = 계열 고유 참조 없음(통과) 의미론 검증.
        return new OSInstallationReferenceInspector(
                new OsMetadataReferenceChecker(osMetadataRepository), isoRepository, List.of(rhelFamily));
    }

    private static RHELInstallationRequest rhel(Long osMetadataId) {
        return new RHELInstallationRequest(
                osMetadataId, 100L,
                new TimezoneRequest("Asia/Seoul", true),
                List.of(new PartitionRequest("/", FileSystem.XFS, null, 0L, SizeUnit.GB, true)),
                null, List.of(), 1L, List.of(), true, null);
    }

    private static UbuntuInstallationRequest ubuntu(Long osMetadataId) {
        // rootPassword 파라미터 없음 — Ubuntu 계약에서 제거됨(root 잠금 기본).
        return new UbuntuInstallationRequest(osMetadataId, 100L,
                new TimezoneRequest("Asia/Seoul", true),
                List.of(new PartitionRequest("/", FileSystem.EXT4, null, 0L, SizeUnit.GB, true)),
                List.of(), "node-01", List.of());
    }

    /** 유효한 ISO(선택 OS 소속·enabled) 스텁 — validate 의 ISO 가드 통과용. */
    private void stubUsableIso(Long isoId, Long osMetadataId) {
        var iso = Mockito.mock(com.example.serverprovision.management.os.entity.ISO.class);
        var owner = Mockito.mock(OSMetadata.class);
        Mockito.lenient().when(owner.getId()).thenReturn(osMetadataId);
        Mockito.lenient().when(iso.isDeleted()).thenReturn(false);
        Mockito.lenient().when(iso.getOsMetadata()).thenReturn(owner);
        Mockito.lenient().when(iso.isEnabled()).thenReturn(true);
        Mockito.lenient().when(iso.isDeprecated()).thenReturn(false);
        given(isoRepository.findById(isoId)).willReturn(Optional.of(iso));
    }

    private OSMetadata enabledOs(boolean deprecated) {
        OSMetadata os = Mockito.mock(OSMetadata.class);
        // describe 경로는 isEnabled 를 호출하지 않으므로 lenient (validate/describe 겸용 fixture).
        Mockito.lenient().when(os.isEnabled()).thenReturn(true);
        Mockito.lenient().when(os.isDeprecated()).thenReturn(deprecated);
        Mockito.lenient().when(os.getOsName()).thenReturn(OSName.ROCKY_LINUX);
        Mockito.lenient().when(os.getOsVersion()).thenReturn("9.4");
        return os;
    }

    @Test
    @DisplayName("공통 — OS 부존재 404 · disabled 409(field=osMetadataId)")
    void common_guards() {
        inspector = build();
        given(osMetadataRepository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> inspector.validateReferences(rhel(99L), CTX))
                .isInstanceOf(OSMetadataNotFoundException.class);

        OSMetadata disabled = Mockito.mock(OSMetadata.class);
        given(disabled.isEnabled()).willReturn(false);
        Mockito.lenient().when(disabled.getOsName()).thenReturn(OSName.ROCKY_LINUX);
        Mockito.lenient().when(disabled.getOsVersion()).thenReturn("9.4");
        given(osMetadataRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(disabled));
        assertThatThrownBy(() -> inspector.validateReferences(rhel(2L), CTX))
                .isInstanceOf(DisabledResourceReferenceException.class)
                .hasFieldOrPropertyWithValue("fieldName", "osMetadataId");
    }

    @Test
    @DisplayName("2단 위임 — RHEL 은 family 빈 호출, Debian(미등록 계열)은 공통만으로 통과")
    void familyDispatch() {
        inspector = build();
        OSMetadata os = enabledOs(false);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(os));
        stubUsableIso(100L, 1L);

        OSInstallationRequest rhelRequest = rhel(1L);
        inspector.validateReferences(rhelRequest, CTX);
        verify(rhelFamily).validateReferences(rhelRequest); // 계열 고유 검증 위임

        assertThatCode(() -> inspector.validateReferences(ubuntu(1L), CTX)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ISO 가드(U2-4) — 타 OS 소속/부존재 404 · disabled 409(field=isoId)")
    void isoGuard() {
        inspector = build();
        OSMetadata os = enabledOs(false);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(os));

        // 타 OS 의 ISO forging → 404
        var foreignIso = Mockito.mock(com.example.serverprovision.management.os.entity.ISO.class);
        var otherOs = Mockito.mock(OSMetadata.class);
        given(otherOs.getId()).willReturn(2L);
        given(foreignIso.isDeleted()).willReturn(false);
        given(foreignIso.getOsMetadata()).willReturn(otherOs);
        given(isoRepository.findById(100L)).willReturn(Optional.of(foreignIso));
        assertThatThrownBy(() -> inspector.validateReferences(rhel(1L), CTX))
                .isInstanceOf(com.example.serverprovision.management.os.exception.ISONotFoundException.class);

        // disabled ISO → 409 field=isoId
        var disabledIso = Mockito.mock(com.example.serverprovision.management.os.entity.ISO.class);
        var owner = Mockito.mock(OSMetadata.class);
        given(owner.getId()).willReturn(1L);
        given(disabledIso.isDeleted()).willReturn(false);
        given(disabledIso.getOsMetadata()).willReturn(owner);
        given(disabledIso.isEnabled()).willReturn(false);
        Mockito.lenient().when(disabledIso.getId()).thenReturn(100L);
        given(isoRepository.findById(100L)).willReturn(Optional.of(disabledIso));
        assertThatThrownBy(() -> inspector.validateReferences(rhel(1L), CTX))
                .isInstanceOf(DisabledResourceReferenceException.class)
                .hasFieldOrPropertyWithValue("fieldName", "isoId");
    }

    @Test
    @DisplayName("deprecated 서술 — 공통(OS) + 계열 서술 합산")
    void describeDeprecated_merges() {
        inspector = build();
        OSMetadata deprecatedOs = enabledOs(true);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(deprecatedOs));
        given(rhelFamily.describeDeprecatedReferences(any())).willReturn(List.of());

        assertThat(inspector.describeDeprecatedReferences(rhel(1L)))
                .containsExactly("Rocky Linux 9.4");
    }
}

package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsAdministratorPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.exception.InvalidWindowsImageSelectionException;
import com.example.serverprovision.provisioning.setting.exception.UnsupportedOsInstallTargetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/** E4-1-a-2 CP4 — Windows 계열 검사기: 대상 정책(리눅스 위조 · 소스 미준비) + 설치 이미지 실재. */
@ExtendWith(MockitoExtension.class)
class WindowsInstallationFamilyInspectorTest {

    private static final WindowsImageName STANDARD = new WindowsImageName("Windows Server 2025 SERVERSTANDARD");
    private static final WindowsImage STANDARD_IMAGE = new WindowsImage(2, STANDARD,
            "Windows Server 2025 Standard (데스크톱 환경)", "ServerStandard", "Server", "ko-KR", "10.0.26100.1742");

    @Mock OSMetadataRepository osMetadataRepository;
    @Mock WindowsImageCatalog catalog;

    private WindowsInstallationFamilyInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new WindowsInstallationFamilyInspector(new OsMetadataReferenceChecker(osMetadataRepository), catalog);
    }

    private void stubOs(OSName osName) {
        OSMetadata os = Mockito.mock(OSMetadata.class);
        Mockito.lenient().when(os.isEnabled()).thenReturn(true);
        Mockito.lenient().when(os.getOsName()).thenReturn(osName);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(os));
    }

    private static WindowsInstallationRequest request(WindowsImageName image) {
        return new WindowsInstallationRequest(2L, 60L, image, new WindowsAdministratorPasswordRequest("S3rver!2025", false));
    }

    private static InstallSourceSnapshot ready() {
        return InstallSourceSnapshot.present(List.of(STANDARD_IMAGE), 1L, Instant.now());
    }

    @Test
    @DisplayName("family = WINDOWS · Windows OS + 준비된 소스에 있는 이미지 → 통과 · deprecated 서술 없음")
    void happy() {
        stubOs(OSName.WINDOWS_SERVER);
        given(catalog.snapshot()).willReturn(ready());

        assertThat(inspector.family()).isEqualTo(OSFamily.WINDOWS);
        assertThatCode(() -> inspector.validateReferences(request(STANDARD))).doesNotThrowAnyException();
        assertThat(inspector.describeDeprecatedReferences(request(STANDARD))).isEmpty();
    }

    @Test
    @DisplayName("리눅스 OS 에 WINDOWS 판별자 위조 → 400(osMetadataId) · 사유 = 정책 문장(옵션 tooltip 과 동일)")
    void linuxTargetForged() {
        stubOs(OSName.ROCKY_LINUX);
        given(catalog.snapshot()).willReturn(ready());

        assertThatThrownBy(() -> inspector.validateReferences(request(STANDARD)))
                .isInstanceOfSatisfying(UnsupportedOsInstallTargetException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(OsInstallTargetPolicy.LINUX_BLOCK_REASON);
                    assertThat(e.fieldName()).isEqualTo("osMetadataId");
                });
    }

    @Test
    @DisplayName("설치 소스 미준비 → 400(osMetadataId) · 사유 = SOURCE_BLOCK_REASON")
    void sourceNotReady() {
        stubOs(OSName.WINDOWS_SERVER);
        given(catalog.snapshot()).willReturn(InstallSourceSnapshot.missing());

        assertThatThrownBy(() -> inspector.validateReferences(request(STANDARD)))
                .isInstanceOfSatisfying(UnsupportedOsInstallTargetException.class,
                        e -> assertThat(e.getMessage()).isEqualTo(OsInstallTargetPolicy.SOURCE_BLOCK_REASON));
    }

    @Test
    @DisplayName("소스에 없는 이미지 → 400(imageName) · 문장에 이미지 이름 포함")
    void imageNotInSource() {
        stubOs(OSName.WINDOWS_SERVER);
        given(catalog.snapshot()).willReturn(ready());
        WindowsImageName datacenter = new WindowsImageName("Windows Server 2025 SERVERDATACENTER");

        assertThatThrownBy(() -> inspector.validateReferences(request(datacenter)))
                .isInstanceOfSatisfying(InvalidWindowsImageSelectionException.class, e -> {
                    assertThat(e.fieldName()).isEqualTo("imageName");
                    assertThat(e.getMessage()).contains("Windows Server 2025 SERVERDATACENTER");
                });
    }
}

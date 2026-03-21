package com.example.serverprovision.domain.os.entity;

import com.example.serverprovision.domain.os.dto.OSMetadataCreateDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataUpdateDTO;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "os_metadata")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OSMetadata extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "os_name", nullable = false)
    private String osName;

    @Column(name = "os_version", nullable = false)
    private String osVersion;

    @Column(name = "iso_mount_path", nullable = false)
    private String isoMountPath;

    @Column(name = "ks_template_path")
    private String ksTemplatePath;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    // 활성화 상태 토글 메서드
    public void toggleEnabled() {
        this.isEnabled = !this.isEnabled;
    }

    public static OSMetadata createFrom(OSMetadataCreateDTO createDTO) {
        return OSMetadata.builder()
                .osName(createDTO.osName())
                .osVersion(createDTO.osVersion())
                .isoMountPath(createDTO.isoMountPath())
                .ksTemplatePath(createDTO.ksTemplatePath())
                .isEnabled(createDTO.isEnabled())
                .build();
    }

    public static OSMetadata updateFrom(OSMetadataUpdateDTO updateDTO) {
        return OSMetadata.builder()
                .id(updateDTO.targetId())
                .osName(updateDTO.osName())
                .osVersion(updateDTO.osVersion())
                .isoMountPath(updateDTO.isoMountPath())
                .ksTemplatePath(updateDTO.ksTemplatePath())
                .isEnabled(updateDTO.isEnabled())
                .build();
    }
}

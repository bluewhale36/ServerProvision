package com.example.serverprovision.domain.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "os_environment")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OSEnvironment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "os_metadata_id", nullable = false)
    private OSMetadata osMetadata;

    @Column(name = "environment_code", nullable = false)
    private String environmentCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;


    @OneToMany
    @JoinColumn(name = "os_environment_id")
    private List<OSPackageGroup> packageGroupList;
}

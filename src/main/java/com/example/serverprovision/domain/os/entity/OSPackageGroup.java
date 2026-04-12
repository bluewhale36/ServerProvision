package com.example.serverprovision.domain.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "os_package_group")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OSPackageGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "os_environment_id", nullable = false)
    private OSEnvironment osEnvironment;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}

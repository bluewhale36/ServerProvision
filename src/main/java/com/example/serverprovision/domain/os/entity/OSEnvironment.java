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


    // mappedBy 로 OSPackageGroup.osEnvironment(@ManyToOne)을 FK 소유자로 지정한다.
    // @JoinColumn 을 @OneToMany 쪽에 두면 Hibernate 가 FK 를 NULL 로 업데이트 후 삭제하려 해
    // nullable = false 제약 조건과 충돌할 수 있다.
    @OneToMany(mappedBy = "osEnvironment", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<OSPackageGroup> packageGroupList;
}

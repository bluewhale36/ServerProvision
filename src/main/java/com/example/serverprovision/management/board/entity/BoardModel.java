package com.example.serverprovision.management.board.entity;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.management.board.enums.Vendor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 메인보드 모델 — (Vendor, model) 조합의 유일한 논리적 단위.
 *
 * <p>MK2 — {@link LifecycleEntity} 상속으로 lifecycle 4 boolean (is_enabled / is_deprecated /
 * is_deleted / deprecated_at) + audit + 가드 로직을 super 가 보유한다. 본 엔티티는 BoardModel 고유
 * 필드(vendor / modelName / description) 만 책임진다. 활성 자식(BIOS / BMC) 동반 soft-delete 정책은
 * 도메인 cascade 가 다르므로 Service 레이어가 처리한다 (super 의 {@code softDelete} override 하지 않음).</p>
 */
@Entity
@Table(name = "board_model")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class BoardModel extends LifecycleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor", nullable = false, length = 32)
    private Vendor vendor;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "description", length = 1024)
    private String description;

    // ---- LifecycleEntity 가드 메시지용 -----------------------------------

    @Override
    protected Long resourceId() {
        return this.id;
    }

    @Override
    protected String resourceLabel() {
        return "메인보드 모델";
    }

    // ---- 도메인 메서드 -------------------------------------------------

    public void update(String modelName, String description) {
        this.modelName = modelName;
        this.description = description;
    }
}

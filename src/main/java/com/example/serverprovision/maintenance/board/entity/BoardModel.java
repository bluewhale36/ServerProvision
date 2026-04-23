package com.example.serverprovision.maintenance.board.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.maintenance.board.enums.Vendor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메인보드 모델 — (Vendor, model) 조합의 유일한 논리적 단위.
 * A3/A4/A5 의 BoardBIOS / BoardBMC / DriverPackage 가 이 엔티티를 FK 로 참조한다 (Stage 1 후속 슬라이스에서 도입).
 * 삭제는 soft 삭제 ({@code isDeleted}) — 자식이 도입된 뒤에는 삭제 시 활성 자식 동반 처리를 이 엔티티 도메인 메서드에 추가한다.
 */
@Entity
@Table(name = "board_model")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoardModel extends BaseTimeEntity {

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

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    // ---- 도메인 메서드 -------------------------------------------------

    public void toggleEnabled() {
        this.isEnabled = !this.isEnabled;
    }

    public void softDelete() {
        this.isDeleted = true;
    }

    public void restore() {
        this.isDeleted = false;
    }

    public void update(String modelName, String description) {
        this.modelName = modelName;
        this.description = description;
    }
}

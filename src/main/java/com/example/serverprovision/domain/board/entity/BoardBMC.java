package com.example.serverprovision.domain.board.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "board_bmc")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class BoardBMC extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "compatible_model_id")
    private BoardModel compatibleModel;

    @Column(name = "version")
    private String version;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "description")
    private String description;

    @Column(name = "is_enabled")
    private boolean isEnabled;
}

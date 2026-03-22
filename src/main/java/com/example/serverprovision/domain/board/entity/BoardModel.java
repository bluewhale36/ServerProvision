package com.example.serverprovision.domain.board.entity;

import com.example.serverprovision.domain.node.model.enums.Vendor;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "board_model")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class BoardModel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor")
    private Vendor vendor;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "description")
    private String description;

    @Column(name = "is_enabled")
    private boolean isEnabled;

    @OneToMany(
            fetch = FetchType.LAZY,
            cascade = CascadeType.REMOVE, orphanRemoval = true,
            mappedBy = "compatibleModel"
    )
    @ToString.Exclude
    private List<BoardBIOS> boardBIOSList;

    @OneToMany(
            fetch = FetchType.LAZY,
            cascade = CascadeType.REMOVE, orphanRemoval = true,
            mappedBy = "compatibleModel"
    )
    @ToString.Exclude
    private List<BoardBMC> boardBMCList;
}

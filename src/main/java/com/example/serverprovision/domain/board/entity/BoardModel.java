package com.example.serverprovision.domain.board.entity;

import com.example.serverprovision.domain.node.model.enums.Vendor;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/**
 * 서버 메인보드 모델을 표현하는 엔티티이다.
 *
 * <p>역할: 물리 서버에 장착된 메인보드의 제조사({@code vendor})와 모델명({@code modelName})을
 * 관리한다. 메인보드 모델 하나에 호환되는 BIOS 버전 목록({@code boardBIOSList})과
 * BMC 펌웨어 버전 목록({@code boardBMCList})이 일대다 관계로 연결된다.
 * {@code isEnabled} 플래그로 세팅 주문서 생성 폼에 노출될 활성 모델을 제어한다.</p>
 *
 * <p>유스케이스: PXE 부팅 요청 시 {@code ServerNodeService#getOrRegisterNode}가
 * {@code BoardModelRepository#findByVendorAndModelName}으로 이 엔티티를 조회해
 * {@code ServerNode}에 연결한다. 세팅 주문서 생성 폼에서는
 * {@code BoardModelService#getViewModelList}가 이 엔티티를 {@code BoardModelDTO}로
 * 변환하여 {@code BoardOptionSelectView}에 담아 프론트엔드에 전달한다.
 * {@code BoardModel}이 삭제되면 {@code CascadeType.REMOVE}로 연결된 {@code BoardBIOS},
 * {@code BoardBMC} 레코드도 함께 삭제된다.</p>
 *
 * <p>확장 가이드: 새 제조사를 지원하려면 {@code Vendor} 열거형에 값을 추가한다.
 * 메인보드 하드웨어 속성(예: 슬롯 수, 소켓 타입)을 추가할 경우 이 엔티티에 필드를 추가하고
 * {@code BoardModelDTO}와 세팅 주문서 프론트엔드 폼도 함께 갱신한다.
 * {@code isEnabled} 필드의 토글 기능이 필요하면 {@code OSMetadata#toggleEnabled}처럼
 * 전용 메소드를 추가한다.</p>
 */
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

    /** 메인보드 제조사. {@code Vendor} 열거형 값이 문자열로 DB에 저장된다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "vendor")
    private Vendor vendor;

    /** 메인보드 모델명. {@code vendor}와 조합하여 {@code PXE} 부팅 요청 시 식별에 사용된다. */
    @Column(name = "model_name")
    private String modelName;

    @Column(name = "description")
    private String description;

    /**
     * 활성화 여부. {@code false}이면 {@code BoardModelService#getAllActiveBoardModel}에서
     * 제외되어 세팅 주문서 생성 폼에 노출되지 않는다.
     */
    @Column(name = "is_enabled")
    private boolean isEnabled;

    /**
     * 이 모델과 호환되는 BIOS 버전 목록. {@code BoardBIOS#compatibleModel}이 FK 소유자이며,
     * 이 모델 삭제 시 연결된 {@code BoardBIOS} 레코드도 연쇄 삭제된다.
     */
    @OneToMany(
            fetch = FetchType.LAZY,
            cascade = CascadeType.REMOVE, orphanRemoval = true,
            mappedBy = "compatibleModel"
    )
    @ToString.Exclude
    private List<BoardBIOS> boardBIOSList;

    /**
     * 이 모델과 호환되는 BMC 펌웨어 버전 목록. {@code BoardBMC#compatibleModel}이 FK 소유자이며,
     * 이 모델 삭제 시 연결된 {@code BoardBMC} 레코드도 연쇄 삭제된다.
     */
    @OneToMany(
            fetch = FetchType.LAZY,
            cascade = CascadeType.REMOVE, orphanRemoval = true,
            mappedBy = "compatibleModel"
    )
    @ToString.Exclude
    private List<BoardBMC> boardBMCList;
}

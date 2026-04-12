package com.example.serverprovision.domain.board.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 메인보드 모델과 호환되는 BMC 펌웨어 버전 정보를 표현하는 엔티티이다.
 *
 * <p>역할: 특정 {@code BoardModel}에 적용 가능한 BMC(Baseboard Management Controller)
 * 펌웨어 버전 하나를 나타낸다. {@code filePath}에 펌웨어 바이너리 파일의 서버 내 경로가
 * 저장되며, {@code isEnabled}로 세팅 주문서 생성 폼의 BMC 선택 목록 노출 여부를 제어한다.</p>
 *
 * <p>유스케이스: {@code BoardModelService#getViewModelList}가 전체 {@code BoardBMC}를
 * {@code BoardBMCDTO}로 변환한 뒤 {@code BoardOptionSelectView#of}에서 보드 모델별로
 * 필터링하여 세팅 주문서 생성 폼에 전달한다. 세팅 주문서 생성 시
 * {@code BasicUpdateResolver}가 이 엔티티를 ID로 조회하여 {@code BasicUpdate} 도메인 모델에
 * 포함시킨다.</p>
 *
 * <p>확장 가이드: BMC 업데이트 프로비저닝 전략({@code ProvisioningStrategy} 구현체) 구현 시
 * {@code filePath}와 {@code compatibleModel}의 IPMI 접속 정보를 조합하여 원격 펌웨어 업데이트를
 * 수행한다. {@code BoardBIOS}와 구조가 동일하므로 확장 방식도 동일하게 적용한다.</p>
 */
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

    /** 이 BMC 버전이 호환되는 메인보드 모델. FK는 {@code compatible_model_id} 컬럼에 저장된다. */
    @ManyToOne
    @JoinColumn(name = "compatible_model_id")
    private BoardModel compatibleModel;

    /** BMC 펌웨어 버전 문자열. 세팅 주문서 생성 폼에서 선택 항목으로 표시된다. */
    @Column(name = "version")
    private String version;

    /** BMC 펌웨어 바이너리 파일의 서버 내 절대 경로. 프로비저닝 시 원격 펌웨어 업데이트에 사용된다. */
    @Column(name = "file_path")
    private String filePath;

    @Column(name = "description")
    private String description;

    /**
     * 활성화 여부. {@code false}이면 세팅 주문서 생성 폼의 BMC 선택 목록에서 제외된다.
     */
    @Column(name = "is_enabled")
    private boolean isEnabled;
}

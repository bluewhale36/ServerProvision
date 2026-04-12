package com.example.serverprovision.domain.board.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 메인보드 모델과 호환되는 BIOS 버전 정보를 표현하는 엔티티이다.
 *
 * <p>역할: 특정 {@code BoardModel}에 적용 가능한 BIOS 버전 하나를 나타낸다.
 * {@code filePath}에 BIOS 바이너리 파일의 서버 내 경로가 저장되며, {@code isEnabled}로
 * 세팅 주문서 생성 폼의 BIOS 선택 목록 노출 여부를 제어한다.</p>
 *
 * <p>유스케이스: {@code BoardModelService#getViewModelList}가 전체 {@code BoardBIOS}를
 * {@code BoardBIOSDTO}로 변환한 뒤 {@code BoardOptionSelectView#of}에서 각 보드 모델에
 * 해당하는 항목만 필터링하여 세팅 주문서 생성 폼에 전달한다. 세팅 주문서 생성 시
 * {@code BasicUpdateResolver}가 이 엔티티를 ID로 조회하여 {@code BasicUpdate} 도메인 모델에
 * 포함시킨다.</p>
 *
 * <p>확장 가이드: BIOS 업데이트 프로비저닝 전략({@code ProvisioningStrategy} 구현체) 구현 시
 * {@code filePath}를 통해 실제 BIOS 파일에 접근한다. 파일 관리 방식이 변경되면(예: 오브젝트
 * 스토리지 URL로 전환) {@code filePath} 필드의 의미가 달라지므로 관련 프로비저닝 전략을
 * 함께 수정한다.</p>
 */
@Entity
@Table(name = "board_bios")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class BoardBIOS extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 BIOS 버전이 호환되는 메인보드 모델. FK는 {@code compatible_model_id} 컬럼에 저장된다. */
    @ManyToOne
    @JoinColumn(name = "compatible_model_id")
    private BoardModel compatibleModel;

    /** BIOS 버전 문자열. 세팅 주문서 생성 폼에서 선택 항목으로 표시된다. */
    @Column(name = "version")
    private String version;

    /** BIOS 바이너리 파일의 서버 내 절대 경로. 프로비저닝 시 펌웨어 업데이트에 사용된다. */
    @Column(name = "file_path")
    private String filePath;

    @Column(name = "description")
    private String description;

    /**
     * 활성화 여부. {@code false}이면 세팅 주문서 생성 폼의 BIOS 선택 목록에서 제외된다.
     */
    @Column(name = "is_enabled")
    private boolean isEnabled;
}

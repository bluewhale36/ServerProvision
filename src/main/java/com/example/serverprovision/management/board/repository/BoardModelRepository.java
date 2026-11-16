package com.example.serverprovision.management.board.repository;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * BoardModel 영속 연산.
 * A3/A4/A5 가 추가된 뒤 호환 목록 Count/Fetch 가 필요해지면 여기에 @EntityGraph 또는 JPQL 을 덧붙인다.
 */
public interface BoardModelRepository extends JpaRepository<BoardModel, Long> {

	/**
	 * 기본 보기 : soft 삭제된 레코드 제외. Vendor 오름차순 → 등록일 내림차순(최신 등록이 상단).
	 */
	List<BoardModel> findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

	/**
	 * 휴지통 포함 보기 : 모든 레코드.
	 */
	List<BoardModel> findAllByOrderByVendorAscCreatedAtDesc();

	/**
	 * 단건 조회 (삭제된 레코드 제외). 수정/토글/삭제 시 사용.
	 */
	Optional<BoardModel> findByIdAndIsDeletedFalse(Long id);

	/**
	 * 복구 조회 (삭제된 레코드만 대상).
	 */
	Optional<BoardModel> findByIdAndIsDeletedTrue(Long id);

	/**
	 * 복구 가드 — 휴지통 행을 되살리려는 (vendor, modelName) 이 활성으로 이미 있는가.
	 * HF26 의 복합 UNIQUE 이후로는 생기지 않는 상황이지만 복구 경로의 안전망으로 둔다.
	 */
	boolean existsByVendorAndModelNameAndIsDeletedFalse(Vendor vendor, String modelName);

	/**
	 * S5-2-3+ — soft-deleted Board (메타 자원). 휴지통 표시용.
	 */
	List<BoardModel> findAllByIsDeletedTrue();

	/**
	 * PXE 부팅 등록 — iPXE 가 보고한 (vendor, modelName) 으로 활성 보드 모델 단건 조회.
	 * 정확 일치 + soft-delete 제외로, 폐기/미등록 모델에 서버를 매핑하는 사고를 차단한다.
	 */
	Optional<BoardModel> findByVendorAndModelNameAndIsDeletedFalse(Vendor vendor, String modelName);

	/**
	 * HF26 — (제조사, 모델명) 단건. 복합 UNIQUE({@code uk_board_model_vendor_model_name})라 상태(활성 · Deprecated · 휴지통)와 무관하게
	 * 최대 한 행이다. 등록 · 이름 수정의 중복 판정이 쓴다.
	 */
	Optional<BoardModel> findByVendorAndModelName(Vendor vendor, String modelName);
}

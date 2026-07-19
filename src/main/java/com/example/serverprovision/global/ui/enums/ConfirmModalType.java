package com.example.serverprovision.global.ui.enums;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.global.ui.exception.ModalContextNotFoundException;
import org.springframework.ui.Model;

/**
 * S5-6 — modal fragment lazy-load 의 modal 종류. abstract method 다형성으로 modalType 별 처리
 * (자원 lookup / expected value 계산 / fragment view 선택) 분기 흡수.
 *
 * <p>CLAUDE.md 의 "조건 분기문 무분별 확장 금지" 원칙 — controller 가 if/switch 로 분기하지 않고
 * enum 상수의 method 호출만으로 modal 별 처리 완료.</p>
 *
 * <p>본 CP2 시점 : S5-6-1 의 2 상수 (PURGE / NUDGE_REPLACE) 만 정의. S5-6-2 / S5-6-3 진입 시
 * 추가 상수가 본 enum 에 합류.</p>
 */
public enum ConfirmModalType {

	/**
	 * S5-6-1 — 영구삭제 (purge) 확인 modal. 자원의 displayName 을 expected value 로 박아 fragment 응답.
	 * 사용자가 입력한 typed-name 과 일치 시에만 form resubmit 활성.
	 */
	PURGE {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			String expected = verifier.resolveExpectedName(resourceType, resourceId);
			model.addAttribute("expectedName", expected);
			model.addAttribute("resourceType", resourceType.name());
			model.addAttribute("resourceId", resourceId);
		}

		@Override
		public String fragmentView() {
			return "fragments/management/confirm-purge :: modalCard";
		}
	},

	/**
	 * S5-6-2 — soft-delete 확인 modal. 자원 lookup 불요 — 표시 정보 (label, extra) 는
	 * JS 가 form 의 data 속성으로 inject.
	 */
	SOFT_DELETE {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			// 자원 lookup 없음. resourceType 만 model 에 표기 (디버그용).
			model.addAttribute("resourceType", resourceType.name());
			model.addAttribute("resourceId", resourceId);
		}

		@Override
		public String fragmentView() {
			return "fragments/management/confirm-soft-delete :: modalCard";
		}
	},

	/**
	 * S5-6-2 — deprecate 확인 modal. SOFT_DELETE 와 동일 패턴.
	 */
	DEPRECATE {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			model.addAttribute("resourceType", resourceType.name());
			model.addAttribute("resourceId", resourceId);
		}

		@Override
		public String fragmentView() {
			return "fragments/management/confirm-deprecate :: modalCard";
		}
	},

	/**
	 * R1-3 — undeprecate 확인 modal. DEPRECATE 의 역동작 2 차 확인.
	 */
	UNDEPRECATE {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			model.addAttribute("resourceType", resourceType.name());
			model.addAttribute("resourceId", resourceId);
		}

		@Override
		public String fragmentView() {
			return "fragments/management/confirm-undeprecate :: modalCard";
		}
	},

	/**
	 * S5-6-2 — restore 확인 modal. cascade preview 정보 (하위 자원 목록 등) 는 JS 가
	 * form 의 data-cascade-true-title / data-cascade-true-desc 에서 inject.
	 */
	RESTORE {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			model.addAttribute("resourceType", resourceType.name());
			model.addAttribute("resourceId", resourceId);
		}

		@Override
		public String fragmentView() {
			return "fragments/management/confirm-restore :: modalCard";
		}
	},

	/**
	 * HF4-1 — 휴지통 보존기간 연장 확인 modal. SOFT_DELETE 와 동일 패턴 — 자원 lookup 불요,
	 * 표시 정보 (자원 라벨·가산 step 일수) 는 JS 가 form 의 data 속성으로 inject.
	 * 종전에는 연장 form 이 deprecate modal 을 차용해 제목/버튼이 "Deprecated 표시" 로 뜨던 것의 해소 (F-2).
	 */
	EXTEND_TTL {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			// 자원 lookup 없음. resourceType 만 model 에 표기 (디버그용).
			model.addAttribute("resourceType", resourceType.name());
			model.addAttribute("resourceId", resourceId);
		}

		@Override
		public String fragmentView() {
			return "fragments/management/confirm-extend :: modalCard";
		}
	},

	/**
	 * R9-3 — 드리프트 적용 확인 modal. 자원 lookup 불요 — 문구(라벨·권장 조치)는 JS 가 form 의
	 * data 속성(R9-2 산출)에서 inject. 종전에는 restore modal 을 차용해 "복구할까요?" 가 떠
	 * GHOST_DB_ROW(행 영구 삭제)와 반대 의미를 안내하던 것의 해소.
	 */
	DRIFT_APPLY {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			model.addAttribute("resourceType", resourceType.name());
			model.addAttribute("resourceId", resourceId);
		}

		@Override
		public String fragmentView() {
			return "fragments/maintenance/reconciliation-modals :: applyModalCard";
		}
	},

	/**
	 * R9-3 — 드리프트 보고 닫기 확인 modal. 종전 soft-delete modal 차용(제목 "자원 삭제")이
	 * "자원이 지워진다" 로 읽히던 것의 해소 — 실동작은 보고 행 제거뿐(자원 무변경, 재보고 가능).
	 */
	DRIFT_DISMISS {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			model.addAttribute("resourceType", resourceType.name());
			model.addAttribute("resourceId", resourceId);
		}

		@Override
		public String fragmentView() {
			return "fragments/maintenance/reconciliation-modals :: dismissModalCard";
		}
	},

	/**
	 * S5-6-3 — 휴지통 액션 결과 안내 modal. 자원 lookup 불요 — JS 가 title / message / hint 모두 inject.
	 * resourceType / resourceId 는 endpoint signature 일관성을 위해 받지만 사용하지 않음.
	 */
	TRASH_RESULT {
		@Override
		public void resolveModel(
				ResourceType resourceType, Long resourceId,
				TypedNameVerifier verifier, Model model
		) {
			// 자원 lookup 없음. fragment markup 만 응답.
		}

		@Override
		public String fragmentView() {
			return "fragments/management/trash-result-modal :: modalCard";
		}
	};

	/**
	 * modal 별 model 준비 책임. resourceType + resourceId 로 자원 lookup, expected value 계산,
	 * model 에 필요한 attribute 주입까지 처리한다. 자원 lookup 실패는
	 * {@link ModalContextNotFoundException} 으로 통일.
	 */
	public abstract void resolveModel(
			ResourceType resourceType, Long resourceId,
			TypedNameVerifier verifier, Model model
	);

	/**
	 * Thymeleaf fragment view 이름 ({@code "templateName :: fragmentName"} 형식).
	 * Controller 가 본 값을 그대로 return.
	 */
	public abstract String fragmentView();
}

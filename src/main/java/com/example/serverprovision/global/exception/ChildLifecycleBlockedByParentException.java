package com.example.serverprovision.global.exception;

import com.example.serverprovision.global.marker.ResourceType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * S5-2-3-1 — 자식 단독 lifecycle action 이 부모 상태로 인해 거절될 때 던진다.
 *
 * <p>거절 사유 예시 :
 * <ul>
 *   <li>부모 OS 가 deleted 상태인데 자식 ISO 만 restore 시도</li>
 *   <li>부모 OS 가 deprecated 상태인데 자식 ISO 만 undeprecate 시도</li>
 *   <li>부모 OS 가 disabled 상태인데 자식 ISO 만 enable 시도</li>
 *   <li>Board ↔ BIOS / BMC 동일 패턴</li>
 * </ul>
 *
 * <p>{@code @ResponseStatus(CONFLICT)} 로 자동 매핑되어 409 응답. {@link ApiExceptionHandler}
 * 의 {@code handleDomain} fallback 이 어노테이션 우선 검사로 자동 처리.</p>
 *
 * <p>UI 측은 message 만으로 사용자 안내 가능하도록 메시지를 자연어로 합성. 부모 자원 정보 (id /
 * resourceType / state) 는 field 로도 노출 — 향후 ApiErrorResponse 확장 시 "부모도 함께 복구" 액션
 * 제안 등 UX 보강 진입점.</p>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ChildLifecycleBlockedByParentException extends ConflictException {

	private final ResourceType parentResourceType;
	private final Long parentResourceId;
	private final String parentState;
	private final ResourceType childResourceType;
	private final Long childResourceId;
	private final String requestedAction;

	public ChildLifecycleBlockedByParentException(
			ResourceType parentResourceType,
			Long parentResourceId,
			String parentState,
			ResourceType childResourceType,
			Long childResourceId,
			String requestedAction,
			String parentDisplayName
	) {
		super(composeMessage(parentDisplayName, parentState, requestedAction));
		this.parentResourceType = parentResourceType;
		this.parentResourceId = parentResourceId;
		this.parentState = parentState;
		this.childResourceType = childResourceType;
		this.childResourceId = childResourceId;
		this.requestedAction = requestedAction;
	}

	/**
	 * 사용자 안내 메시지 합성. 부모 자원명 + 상태 + 권유 액션 1 문장.
	 * 예: "부모 자원 'Rocky Linux 9.5' 이(가) 삭제 상태라 복구할 수 없습니다. 부모부터 복구해야 합니다."
	 * 상태 · 동작 코드(DELETED · restore 등)는 화면 말로 바꿔 쓴다(S22-4 — 코드 원문 비노출).
	 */
	private static String composeMessage(String parentDisplayName, String parentState, String requestedAction) {
		String parentStateKor = switch (parentState) {
			case "DISABLED" -> "비활성";
			case "DEPRECATED" -> "사용 중단";
			case "DELETED" -> "삭제";
			default -> "다른";
		};
		String actionKor = switch (requestedAction) {
			case "enable" -> "활성화할";
			case "restore" -> "복구할";
			case "undeprecate" -> "사용 중단 권고를 해제할";
			default -> "이 작업을 할";
		};
		String suggestion = switch (parentState) {
			case "DISABLED" -> "부모를 먼저 활성화해야 합니다.";
			case "DEPRECATED" -> "부모의 사용 중단 권고를 먼저 해제해야 합니다.";
			case "DELETED" -> "부모부터 복구해야 합니다.";
			default -> "부모 상태를 먼저 확인해야 합니다.";
		};
		return String.format(
				"부모 자원 '%s' 이(가) %s 상태라 %s 수 없습니다. %s",
				parentDisplayName, parentStateKor, actionKor, suggestion
		);
	}

	public ResourceType getParentResourceType() {
		return parentResourceType;
	}

	public Long getParentResourceId() {
		return parentResourceId;
	}

	public String getParentState() {
		return parentState;
	}

	public ResourceType getChildResourceType() {
		return childResourceType;
	}

	public Long getChildResourceId() {
		return childResourceId;
	}

	public String getRequestedAction() {
		return requestedAction;
	}
}

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
public class ChildLifecycleBlockedByParentException extends DomainException {

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
	 * 예: "부모 OS 버전 'Rocky Linux 9.5' 이(가) 삭제 상태입니다. 부모부터 복구해주세요."
	 */
	private static String composeMessage(String parentDisplayName, String parentState, String requestedAction) {
		String parentStateKor = switch (parentState) {
			case "DISABLED" -> "비활성";
			case "DEPRECATED" -> "Deprecated";
			case "DELETED" -> "삭제";
			default -> parentState;
		};
		String suggestion = switch (parentState) {
			case "DISABLED" -> "부모를 먼저 활성화해주세요.";
			case "DEPRECATED" -> "부모를 먼저 Deprecated 해제해주세요.";
			case "DELETED" -> "부모부터 복구해주세요.";
			default -> "부모 상태를 먼저 정합화해주세요.";
		};
		return String.format(
				"부모 자원 '%s' 이(가) %s 상태라 자식의 %s 액션이 거절됐어요. %s",
				parentDisplayName, parentStateKor, requestedAction, suggestion
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

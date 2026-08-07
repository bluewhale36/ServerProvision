package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 할당할 수 없는 상태의 세팅 정의서를 할당 · 재할당하려 할 때 던진다(U3-2-b DEC-G). 정상 흐름은 UI 가 할당
 * 드롭다운에서 그런 정의서를 아예 빼서 1차 차단하므로, 이 예외는 direct POST · 동시성(선택 후 관리자가
 * 비활성화) · stale 화면을 잡는 안전망(409)이다. (advice 가 base {@link ConflictException} 으로 409 매핑.)
 *
 * <p>메시지는 도메인 SSOT {@code SettingDefinition.assignBlockReason()} 이 반환한 사유를 그대로 싣는다 —
 * 드롭다운 필터 판정과 서버 예외 문구가 갈라지지 않게 한다({@code ReassignAfterStartException} 선례 동형).</p>
 */
public class DefinitionNotAssignableException extends ConflictException {

    public DefinitionNotAssignableException(Long definitionId, String reason) {
        super(reason + " definitionId=" + definitionId);
    }
}

package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.provisioning.setting.dto.request.SettingSaveRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSaveResponse;
import com.example.serverprovision.provisioning.setting.exception.RestoreNameConflictException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;

/**
 * 세팅 정의서 쓰기 계약 — 컨트롤러는 이 인터페이스만 본다.
 * (U2-1 스텁 → U2-3 에서 {@link JpaSettingCommandService} 실영속 구현으로 대체.
 *  U3-2-b 에서 DB 전용 soft-delete lifecycle(softDelete/restore/purge) 추가.)
 */
public interface SettingCommandService {

    SettingSaveResponse create(SettingSaveRequest request);

    /**
     * @throws SettingNotFoundException 해당 id 의 활성 정의서가 없을 때 (advice 404)
     */
    SettingSaveResponse update(Long id, SettingSaveRequest request);

    /**
     * soft-delete — 활성 목록에서 감춘다. 멱등. 삭제는 차단이 아니라 경고(DEC-C)라 정상 경로에 예외가 없다.
     *
     * @throws SettingNotFoundException 해당 id 의 정의서가 없을 때 (advice 404)
     */
    void softDelete(Long id);

    /**
     * 복원 — soft-deleted 정의서를 다시 활성으로. 같은 이름의 활성 정의서가 있으면 거절한다(DEC-B).
     *
     * @throws SettingNotFoundException     해당 id 의 정의서가 없을 때 (advice 404)
     * @throws RestoreNameConflictException 같은 이름의 활성 정의서가 이미 존재할 때 (advice 409)
     */
    void restore(Long id);

    /**
     * 영구 삭제 — soft-deleted 정의서만 대상(soft-delete 선행, DEC-E). typed-name 일치 시 hard delete
     * (자식 {@code setting_process} 동반). 소프트참조 할당 스냅샷은 삭제와 무관하게 생존한다(DEC-C).
     *
     * @throws SettingNotFoundException    id 의 soft-deleted 정의서가 없을 때(활성/부재 포함) (advice 404)
     * @throws TypedNameMismatchException  입력 정의서명이 실제 명칭과 불일치할 때 (advice 400)
     */
    void purge(Long id, String typedName);

    /**
     * 활성 ↔ 비활성 반전(U3-2-b DEC-G). 비활성 정의서는 신규 할당이 차단되고 기존 할당 스냅샷은 무영향이다.
     * soft-deleted 정의서는 대상이 아니다 — 복원이 먼저다.
     *
     * @throws SettingNotFoundException 해당 id 의 활성 정의서가 없을 때(삭제분 포함) (advice 404)
     */
    void toggleEnabled(Long id);

    /**
     * 사용 중단 권고 표시(U3-2-b DEC-G). 할당을 막지 않고 경고만 붙인다. 멱등.
     *
     * @throws SettingNotFoundException 해당 id 의 활성 정의서가 없을 때(삭제분 포함) (advice 404)
     */
    void deprecate(Long id);

    /**
     * 사용 중단 권고 해제. 멱등.
     *
     * @throws SettingNotFoundException 해당 id 의 활성 정의서가 없을 때(삭제분 포함) (advice 404)
     */
    void undeprecate(Long id);
}

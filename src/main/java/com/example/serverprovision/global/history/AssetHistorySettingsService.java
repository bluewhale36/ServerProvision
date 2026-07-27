package com.example.serverprovision.global.history;

import com.example.serverprovision.global.history.dto.response.AssetHistorySettingsResponse;

/**
 * 버전 이력 보존 설정의 읽기·갱신(E1-I-2-b-2). {@code prune} 이 실효 보존 개수를 여기서 얻고, 통합 시스템 자산
 * 운영 설정 화면({@code /system/asset/settings})이 현재값을 표시·수정한다. 구현은 singleton 행 +
 * 캐시({@code TrashSettingsService} 미러).
 */
public interface AssetHistorySettingsService {

    /** prune 이 자주 호출하는 실효 보존 개수. 캐시에서 반환한다. */
    int getRetentionCount();

    /** 대시보드 표시용 현재 설정. */
    AssetHistorySettingsResponse current();

    /** 운영자 갱신 — 보존 개수 교체 후 현재 설정 반환. */
    AssetHistorySettingsResponse update(int retentionCount);
}

package com.example.serverprovision.maintenance.reconciliation.repository;

import com.example.serverprovision.maintenance.reconciliation.entity.ReconciliationSetting;
import com.example.serverprovision.maintenance.reconciliation.enums.ReconciliationSettingItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * MK4-3-1 — 운영 설정 항목 저장소. 기본키가 항목 열거형이라 {@code findById(item)} 하나로 충분하고,
 * 한 항목이 두 행을 가질 수 없다는 것도 그 덕에 보장된다.
 */
public interface ReconciliationSettingRepository
		extends JpaRepository<ReconciliationSetting, ReconciliationSettingItem> {
}

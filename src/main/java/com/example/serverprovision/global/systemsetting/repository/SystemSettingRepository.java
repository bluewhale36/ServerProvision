package com.example.serverprovision.global.systemsetting.repository;

import com.example.serverprovision.global.systemsetting.entity.SystemSetting;
import com.example.serverprovision.global.systemsetting.enums.SystemSettingKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    Optional<SystemSetting> findBySettingKey(SystemSettingKey settingKey);
}

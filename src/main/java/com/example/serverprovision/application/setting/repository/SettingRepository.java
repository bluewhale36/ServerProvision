package com.example.serverprovision.application.setting.repository;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettingRepository extends JpaRepository<ServerSetting, Long> {
}

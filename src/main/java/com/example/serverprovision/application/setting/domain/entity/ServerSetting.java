package com.example.serverprovision.application.setting.domain.entity;

import com.example.serverprovision.application.setting.converter.SettingProcessConverter;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.application.setting.model.enums.SettingStatus;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "server_setting")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ServerSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process")
    @Convert(converter = SettingProcessConverter.class)
    private SettingProcess settingProcess;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SettingStatus status;
}

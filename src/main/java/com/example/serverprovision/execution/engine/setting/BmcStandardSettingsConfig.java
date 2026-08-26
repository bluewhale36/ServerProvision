package com.example.serverprovision.execution.engine.setting;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link BmcStandardSettings} 바인딩 활성화 — {@code BiosProvisioningConfig} 와 같은 방식. */
@Configuration
@EnableConfigurationProperties(BmcStandardSettings.class)
public class BmcStandardSettingsConfig {
}

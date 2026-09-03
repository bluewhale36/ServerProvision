package com.example.serverprovision.execution.wininstall.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link WindowsInstallProperties} 바인딩 등록 — 항상 존재하는 빈(미설정은 값으로 표현). */
@Configuration
@EnableConfigurationProperties(WindowsInstallProperties.class)
public class WindowsInstallConfig {
}

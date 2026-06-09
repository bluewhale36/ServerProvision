package com.example.serverprovision.provisioning.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * provisioning BIOS 슬라이스 설정 등록. {@link BiosResourceProperties} 바인딩 활성화.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BiosResourceProperties.class)
public class BiosProvisioningConfig {
}

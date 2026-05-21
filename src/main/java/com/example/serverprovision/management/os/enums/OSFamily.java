package com.example.serverprovision.management.os.enums;

/**
 * OS 계열. 설치 스크립트(Kickstart / cloud-init / unattend.xml) 분기의 상위 축이 된다.
 * Stage 3 PXE 연동에서 {@code ProvisioningStrategy} 가 이 값을 참조한다.
 */
public enum OSFamily {
	RHEL_BASED,
	DEBIAN_BASED,
	WINDOWS_BASED
}

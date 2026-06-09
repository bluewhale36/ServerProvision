package com.example.serverprovision.provisioning.dto.response;

/**
 * 페이지 한 행의 뷰모델. 위젯 / submenu 다형성을 sealed 로 표현하고, Thymeleaf 는 {@link #kind()} 판별자로 분기한다.
 */
public sealed interface BiosRowResponse permits BiosWidgetRowResponse, BiosSubmenuRowResponse {

	/** "widget" | "submenu" — Thymeleaf {@code th:switch} 판별자. */
	String kind();
}

package com.example.serverprovision.provisioning.domain;

/**
 * 페이지 내 1개 컨트롤. leaf(속성 위젯) / submenu(하위 페이지 링크) 다형성을 sealed 로 표현해
 * 렌더 시 instanceof 분기를 패턴 매칭으로 한정한다.
 */
public sealed interface BiosControl permits BiosAttributeControl, BiosSubmenuControl {
}

package com.example.serverprovision.provisioning.domain;

import com.example.serverprovision.provisioning.domain.enums.BiosComplexHint;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

/**
 * leaf control — 1개 속성 위젯 자리. AttributeName(+Complex 힌트)만 보유하며,
 * 표시 메타(타입/옵션/도움말/기본값)는 렌더 시 레지스트리와 조인해 얻는다.
 */
public record BiosAttributeControl(BiosAttributeName name, BiosComplexHint complex) implements BiosControl {
}

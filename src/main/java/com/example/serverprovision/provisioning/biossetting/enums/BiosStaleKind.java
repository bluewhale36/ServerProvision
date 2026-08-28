package com.example.serverprovision.provisioning.biossetting.enums;

import java.util.List;

/** 템플릿 저장값이 레지스트리와 어긋나는 두 방식(E3-3 R4). 문장은 상수가 만든다 — 화면 · 차단 사유 · 원장이 같은 문장을 쓴다. */
public enum BiosStaleKind {

    /** 레지스트리에 그 속성이 없다(BIOS 개정으로 사라짐 · 카탈로그 미보유). */
    MISSING_ATTRIBUTE {
        @Override
        public String message(String attributeName, String storedRaw, List<String> allowed) {
            return attributeName + " — 레지스트리에 없는 속성";
        }
    },
    /** 속성은 있으나 저장값이 허용 목록 밖이다(표기 변경 · 선택지 제거). */
    VALUE_NOT_ALLOWED {
        @Override
        public String message(String attributeName, String storedRaw, List<String> allowed) {
            return attributeName + " = " + storedRaw + " — 허용 {" + String.join(", ", allowed) + "}";
        }
    };

    public abstract String message(String attributeName, String storedRaw, List<String> allowed);
}

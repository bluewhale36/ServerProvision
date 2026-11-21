package com.example.serverprovision.global.systemsetting.enums;

/** 프로비저닝 허용 대역 입력의 위반 종류. 검사 순서대로 선언한다(처음 걸린 하나를 보인다). */
public enum AllowedCidrViolation {

    EMPTY("프로비저닝 허용 대역은 필수 값입니다."),
    FORMAT("'네트워크주소/prefix' 형식으로 입력해야 합니다. 예: 10.1.1.0/24"),
    ADDRESS_INVALID("유효한 주소 값을 입력해야 합니다."),
    PREFIX_OUT_OF_RANGE("유효한 prefix 값을 입력해야 합니다."),
    NOT_PRIVATE("RFC 1918 내부망 대역만 입력해야 합니다: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16"),
    WIDER_THAN_BLOCK("네트워크 주소가 prefix 범위를 초과하지 않아야 합니다."),
    TOO_NARROW("prefix 는 26 이하여야 합니다."),
    NOT_NETWORK_ADDRESS("유효한 네트워크 주소가 아닙니다.");

    private final String message;

    AllowedCidrViolation(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}

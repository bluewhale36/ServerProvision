package com.example.serverprovision.provisioning.biossetting.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * BIOS 카탈로그(레지스트리 · SetupData)가 없는 보드로 편집기에 들어오려 할 때 던진다 — direct GET 안전망.
 * 정상 흐름은 보드 선택 화면이 같은 판정({@code BiosRegistryResolver.available})으로 카드를 disabled 해 먼저 막는다.
 * 종전에는 로더의 IO 실패({@code BiosResourceLoadException})가 500 으로 새어 서버 오류처럼 보였다(S17-3 CP5 F-1).
 */
public class BiosCatalogNotFoundException extends NotFoundException {

    public BiosCatalogNotFoundException(String boardModelName) {
        super("BIOS 카탈로그가 없는 보드입니다. board=" + boardModelName);
    }
}

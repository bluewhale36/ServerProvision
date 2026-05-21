package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * marker 파일의 HMAC 서명이 현재 서버 키로 재계산한 값과 일치하지 않는 상태.
 * 누군가 marker 를 수작업으로 편집했거나 다른 서버에서 옮겨온 경우에 발생.
 */
public class MarkerSignatureMismatchException extends ConflictException {

	public MarkerSignatureMismatchException(String path) {
		super("marker 서명 검증에 실패했습니다. 외부에서 변조됐을 가능성이 있습니다 : " + path);
	}
}

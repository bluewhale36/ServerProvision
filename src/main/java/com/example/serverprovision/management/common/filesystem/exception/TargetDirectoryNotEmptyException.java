package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 업로드 대상 디렉토리에 이미 파일이 존재하지만 A3 가 관리하는 marker({@code .provision.json}) 는 없는 상태.
 * 외부에서 사람이 놓아둔 파일을 덮어쓰는 사고를 막기 위해 사전 거절. 관리자가 수동으로 비우거나 다른 경로를 지정해야 한다.
 */
public class TargetDirectoryNotEmptyException extends ConflictException {

	public TargetDirectoryNotEmptyException(String path) {
		super("대상 디렉토리가 비어있지 않습니다. 수동 정리 후 다시 시도하세요 : " + path);
	}
}

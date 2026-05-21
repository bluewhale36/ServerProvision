package com.example.serverprovision.management.bios.enums;

/**
 * 번들 업로드 방식.
 * <ul>
 *   <li>{@link #FOLDER} — 폴더 1개 업로드. 내용물이 대상 디렉토리로 전개 (wrapping folder 제거).</li>
 *   <li>{@link #ZIP} — zip 파일 1개 업로드. 서버가 전개 후 zip 폐기. flat/wrapped 자동 분기.</li>
 *   <li>{@link #SINGLE_FILE} — 파일 1개 업로드. ASUS BIOS 의 {@code .cap}/{@code .CAP} 처럼
 *       단일 바이너리가 전체 BIOS 내용인 경우. 대상 디렉토리에 해당 파일 1개만 놓이고
 *       자동 진입점 탐지가 그 파일을 대상 파일로 선택한다 (확장자 무관).</li>
 * </ul>
 * Fujitsu iRMC 방식처럼 파일 업로드로 표현 불가한 vendor 는 향후 Execution 도메인에서 별도 bundle type 으로 분리 예정.
 */
public enum BiosUploadMode {
	FOLDER,
	ZIP,
	SINGLE_FILE
}

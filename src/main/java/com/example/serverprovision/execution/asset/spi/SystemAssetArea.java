package com.example.serverprovision.execution.asset.spi;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.exception.SystemAssetSealNotSupportedException;

import java.util.List;

/**
 * 통합 시스템 자산 대시보드의 확장점(Service Provider Interface — 도메인이 구현해 끼우는 확장점). 자산 종류
 * 하나(진단 리눅스·TFTP·향후 추가)가 자기 슬롯 집합과 슬롯별 무결성 판정을 스스로 제공한다. 대시보드
 * 서비스는 {@code List<SystemAssetArea>} 를 주입받아 {@link SystemAssetAreaKey} 로 라우팅할 뿐, 영역이
 * 늘어도 집계·컨트롤러 분기를 늘리지 않는다(조건분기 legacy 확장 금지 — 다형성으로 흡수).
 *
 * <p><b>봉인 대칭성</b>: 봉인은 파일 봉인 기반 영역(진단 자산)에만 의미가 있다. {@link #supportsSeal()}가
 * 기본 true 이나, 봉인 개념이 없는 영역은 이를 false 로 오버라이드하며 그 경우 기본 {@link #seal()}은
 * {@link SystemAssetSealNotSupportedException} 을 던진다(UI 1차 차단의 서버 안전망).</p>
 */
public interface SystemAssetArea {

    /** 이 영역의 라우팅 키. {@code Map<SystemAssetAreaKey, SystemAssetArea>} 조립·URL 경로 변수 매칭에 쓰인다. */
    SystemAssetAreaKey areaKey();

    /** 대시보드에 표시할 영역명 (예: "진단 리눅스 부팅 자산"). */
    String displayName();

    /** 서빙 전제 충족 여부. NOT_CONFIGURED 면 슬롯은 "서빙 비활성" 판정으로만 조회된다. */
    AreaAvailability availability();

    /** 이 영역의 슬롯 메타 전량(고정 집합). 대시보드가 순회하며 {@link #inspect}로 현황을 채운다. */
    List<SystemAssetSlot> slots();

    /**
     * 영역 헤더에 함께 노출할 컨텍스트 관측치(서비스 상태·활성 임대 건수 등). 슬롯이 아니므로 okCount 에 불참한다.
     * 파일 봉인 영역(진단·TFTP)은 관측치가 없어 기본 빈 목록 — 대시보드 헤더에 chip 을 그리지 않는다(무변경).
     */
    default List<AssetContextItem> context() {
        return List.of();
    }

    /**
     * 슬롯 하나의 현재 현황·무결성을 프로브한다. 존재·크기·수정시각·판정을 담은 {@link AssetSlotStatus} 를
     * 반환하며, 부재·미봉인·서빙비활성을 전부 판정으로 흡수하므로 예외로 거절하지 않는다(조회는 실패하지 않음).
     */
    AssetSlotStatus inspect(SystemAssetSlot slot);

    /** 봉인 지원 여부. 파일 봉인 기반 영역만 true. UI 봉인 버튼 노출과 서버 가드가 이 플래그를 공유한다(SSOT). */
    default boolean supportsSeal() {
        return true;
    }

    /**
     * 영역 단위 무결성 봉인 — 존재하는 슬롯의 현재 상태를 신뢰 기준으로 마커에 기록한다. 봉인 미지원 영역은
     * 이 기본 구현이 거절하고, 지원 영역이 실제 기록 로직으로 오버라이드한다.
     */
    default SealResult seal() {
        throw SystemAssetSealNotSupportedException.of(areaKey());
    }
}

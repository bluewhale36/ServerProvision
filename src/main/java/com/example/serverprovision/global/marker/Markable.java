package com.example.serverprovision.global.marker;

import com.example.serverprovision.global.lifecycle.LifecycleManageable;
import com.example.serverprovision.global.lifecycle.LifecycleStage;

import java.nio.file.Path;

/**
 * `.provision.json` 마커가 부착될 수 있는 도메인 자원의 어댑터.
 * <p>각 도메인 엔티티({@code BoardBIOS}, {@code ISO} 등)가 본 인터페이스를 구현해
 * 인프라 측에 자기를 마커 부착 가능 자원으로 노출한다. 인프라(global/marker) 는 도메인 모르고
 * 본 인터페이스 메서드만으로 마커 발급/검증을 처리할 수 있어야 한다.</p>
 *
 * <p>2-phase save 패턴: 등록 직후 PK 가 정해지기 전에는 {@code markerSignature} 가 null 일 수 있다.
 * Service 가 PK 를 채운 entity 를 다시 저장한 뒤 {@link #reissueMarker(String, String)} 으로
 * manifestHash + signature 를 부여한다.</p>
 */
public interface Markable extends LifecycleManageable {

    Long getResourceId();

    ResourceType getResourceType();

    /** 자원 본체의 디스크 경로. IN_TREE 자원은 디렉토리, SIDECAR 자원은 단일 파일. */
    Path getResourcePath();

    /** ResourceType 의 default layout 을 그대로 쓰는 게 일반적. 자원별 override 필요 시 재정의. */
    default MarkerLayout getMarkerLayout() {
        return getResourceType().getDefaultLayout();
    }

    String getManifestHash();

    /** null 이면 마커 미발급 상태 (2-phase save 중간 또는 데이터 마이그레이션 전 자원). */
    String getMarkerSignature();

    /** 새로 계산된 hash + signature 를 엔티티 필드에 반영. 호출자는 이후 repository.save() 로 영속화. */
    void reissueMarker(String manifestHash, String markerSignature);

    /**
     * MK2 — Markable 자원의 lifecycle 어휘. {@link LifecycleManageable} 의 default 구현 그대로 사용.
     * MK1 reconciliation 보고서가 본 메서드로 자원 상태를 분류 (결정 #4 — deprecated 별도 표기).
     */
    default LifecycleStage lifecycleStage() {
        return currentStage();
    }

    /**
     * S5-2 — 사용자 표시용 + 영구삭제 typed-name 검증 기준 자원명.
     *
     * <p>각 도메인 entity 가 자기 합성식을 보유하는 다형성 진입점. 5 list page 의 typed-name 검증,
     * 휴지통 페이지의 displayName + typed-name 검증, modal 메시지의 자원 식별자가 모두 본 메서드를 사용.
     * 합성식이 entity 한 곳에 응집 — service / scanner / controller / view 모두 동일 메서드 호출 (중복 0).</p>
     *
     * <p>합성 예시 :</p>
     * <ul>
     *   <li>OSImage : {@code osName.displayName + " " + osVersion} (예: "Rocky Linux 9.6")</li>
     *   <li>ISO : {@code parent + " " + isoBasename}</li>
     *   <li>BoardModel : {@code vendor.displayName + " " + modelName}</li>
     *   <li>BIOS / BMC / Subprogram : {@code name}</li>
     * </ul>
     *
     * <p>default 는 일반 fallback — entity 가 override 권장.</p>
     */
    default String displayName() {
        return getResourceType().name() + " #" + getResourceId();
    }
}

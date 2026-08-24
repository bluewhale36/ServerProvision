package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.global.redfish.RedfishTarget;

import java.util.Optional;

/**
 * 펌웨어 집행 흐름 SPI(Service Provider Interface — 도메인이 구현해 끼우는 확장점, E2-2 · 재편 토론 Q-J · Q-K).
 * 분할 축은 <b>제조사가 아니라 흐름</b>이다 — 지금 구현체는 "Redfish SimpleUpdate 로 굽고 FirmwareInventory
 * 로 확인하는" 흐름 하나이며, 여러 제조사가 그 하나를 공유한다. 모델별 차이는 데이터(BoardModel)로 두고
 * <b>모델당 코드는 만들지 않는다.</b>
 *
 * <p>연산을 {@code doExecute} 하나로 묶지 않고 단계별로 나눈 것은 집행을 워커가 주도하기 때문이다(D-3).
 * 굽기는 실측에서 축 하나에 최대 8분이 걸리고 그 사이 전원 왕복까지 끼므로, 한 호출 안에서 끝나는 절차가
 * 아니라 <b>여러 주기에 걸쳐 진행되는 상태 기계</b>다. provider 는 그 기계가 부르는 연산을 제공하고,
 * 어디까지 갔는지는 원장이 기억한다.</p>
 *
 * <p>신규 흐름 지원 = 분기 추가가 아니라 <b>빈 등록</b>이다. BMC 가 없어 Redfish 를 쓸 수 없는 보드도
 * 그 보드를 다루는 provider 가 등록되는 순간 풀린다 — 그때까지는 어느 provider 도 지원하지 않으므로
 * 진입이 차단된다(D-6).</p>
 */
public interface FirmwareUpdateProvider {

    /**
     * 이 게스트를 이 흐름으로 다룰 수 있는가(D-6). Redfish 흐름은 BMC 가 검출돼야 성립한다 —
     * 굽는 것도 확인하는 것도 BMC 를 거치기 때문이다.
     *
     * <p>지원 판정을 실행기가 아니라 provider 자신이 하는 것이 SPI 계약의 핵심이다. 실행기는 어떤
     * 흐름이 있는지 알지 않으므로, 흐름이 늘어도 실행기에 분기가 생기지 않는다.</p>
     */
    boolean supports(GuestServer server, GuestServerDetail detail);

    /**
     * 지금 이 주소가 이 게스트의 BMC 인지 확인한다(D-11). 되돌릴 수 없는 조작을 내기 직전과,
     * 그 읽기가 종결 판정의 근거가 될 때 호출한다.
     *
     * <p>대조 기준은 흐름마다 다를 수 있어 여기 둔다 — 지금 구현체는 보드 시리얼을 쓰며, 그 값이
     * 벤더 확장 필드에 있어 표준 경로로는 읽을 수 없다.</p>
     *
     * @param expectedBoardSerial 게스트에 기록된 보드 시리얼(진단 수집값)
     */
    BmcIdentity verifyIdentity(RedfishTarget target, String expectedBoardSerial);

    /**
     * 한 축의 굽기를 시작한다. 반환은 진행을 추적할 Task 경로 — 실측에서 202 응답과 함께 왔다.
     * 경로를 받지 못하면 추적할 수단이 없다는 뜻이므로 비어 있다.
     *
     * @param imageUri BMC 가 당겨 갈 절대 URL(D-5 — 일회용 토큰이 포함된다)
     */
    Optional<String> startFlash(RedfishTarget target, FirmwareAxis axis, String imageUri);

    /** 진행 중인 굽기의 상태를 읽는다. 이것은 관측이라 신원 확인을 앞세우지 않는다(D-11). */
    FlashTaskState pollTask(RedfishTarget target, String taskPath);

    /**
     * 이 축의 현재 펌웨어 버전을 읽는다. 굽기 전 멱등 판정(D-7)과 굽고 난 뒤 반영 확인(8행)이
     * 같은 원천을 쓴다 — 판정과 확인이 다른 곳을 보면 어긋날 자리가 생긴다.
     */
    Optional<String> readVersion(RedfishTarget target, FirmwareAxis axis);
}

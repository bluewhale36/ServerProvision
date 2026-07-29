package com.example.serverprovision.execution.pxeinfra.apply;

import com.example.serverprovision.execution.pxeinfra.command.AllowedCommand;
import com.example.serverprovision.execution.pxeinfra.command.CommandOutcome;
import com.example.serverprovision.execution.pxeinfra.command.CommandResult;
import com.example.serverprovision.execution.pxeinfra.command.SystemCommandRunner;
import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.exception.DhcpServiceControlFailedException;
import com.example.serverprovision.execution.pxeinfra.inspect.SystemServiceInspector;
import com.example.serverprovision.execution.pxeinfra.render.DhcpdConfigRenderer;
import com.example.serverprovision.execution.pxeinfra.spi.ServiceState;
import com.example.serverprovision.global.asset.AtomicAssetSwap;
import com.example.serverprovision.global.asset.SwapResult;
import com.example.serverprovision.global.history.AssetHistoryService;
import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.security.FileSystemHardener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * dhcpd 조각 적용 파이프라인의 비트랜잭션 오케스트레이터. desired 를 조각으로 렌더해 원자 교체하고, {@code dhcpd -t}
 * 게이트를 통과할 때만 서비스를 재기동하며, 재기동·검증이 실패하면 이전 조각으로 되돌린다. 명령 실패는 예외가 아닌
 * {@link CommandResult} 로 흡수하므로 파이프라인은 {@link ApplyOutcome} 으로만 귀결한다(archive IO 실패·미구성만 예외).
 *
 * <p><b>단일 실행 보장</b> : 조각 스왑·재기동은 전역 부작용이라 동시에 두 번 돌면 서로의 롤백을 오염시킨다.
 * {@link #applyAndRecord} 가 프로세스 락 안에서 apply 와 결과 기록(persist)을 함께 감싸, 조각 상태와 DB 기록이
 * 한 임계 구간에서 정합하게 갱신된다(D6). 실패의 HTTP 승격({@link ApplyOutcome#throwIfNotApplied})은 락·트랜잭션
 * 밖에서 호출자가 수행한다(D3 — 트랜잭션 롤백으로 기록이 사라지는 것을 막는다).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DhcpdConfigApplyService {

    /** dhcpd 조각의 버전 이력 category — PXE 인프라 계열. */
    private static final String HISTORY_CATEGORY = "PXE";

    private final SystemCommandRunner commandRunner;
    private final SystemServiceInspector serviceInspector;
    private final AtomicAssetSwap atomicAssetSwap;
    private final DhcpdConfigRenderer renderer;
    private final ObjectProvider<PxeInfraProperties> propertiesProvider;
    private final AssetHistoryService historyService;
    private final FileSystemHardener fileSystemHardener;

    /** 조각 스왑·재기동은 전역 부작용이라 프로세스 단위로 직렬화한다(단일 프로세스·단일 행 전제). */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 프로세스 락 안에서 {@link #apply} 와 결과 기록을 함께 수행한다. persister 는 파이프라인 귀결을 DB 에
     * 반영하는 콜백으로, 락 안에서 커밋까지 끝내 조각 상태와 기록이 한 임계 구간에 묶인다.
     */
    public ApplyOutcome applyAndRecord(PxeNetworkConfig desired, Consumer<ApplyOutcome> persister) {
        lock.lock();
        try {
            ApplyOutcome outcome = apply(desired);
            persister.accept(outcome);   // 락 안에서 기록 커밋(성공·실패 모두 남긴다)
            return outcome;
        } finally {
            lock.unlock();
        }
    }

    /**
     * dhcpd 조각 적용 상태기계. 순서 : 렌더→temp → 원자 스왑(현재본 archive) → {@code dhcpd -t} 게이트 →
     * (통과 시) 재기동 → 서비스 active 검증. 어느 단계든 어긋나면 이전 조각(없으면 빈 조각)으로 복원한다.
     * SELinux 는 disabled 전제라 컨텍스트 복원 단계가 없다.
     */
    public ApplyOutcome apply(PxeNetworkConfig desired) {
        PxeInfraProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            // 조각 경로 미구성 — 적용을 시도할 전제 자체가 없다(인프라 구성 결함 → 500).
            throw new DhcpServiceControlFailedException(
                    "dhcpd 조각 경로가 구성되지 않았습니다(pxe.dhcpd.fragment-path 미설정). 서버 구성을 확인하세요.");
        }
        Path fragmentPath = props.getFragmentPath();
        Path fragmentDir = fragmentPath.getParent();
        Path dhcpdConfPath = props.getDhcpdConfPath();
        AssetVersionKey key = new AssetVersionKey(HISTORY_CATEGORY, fragmentPath.getFileName().toString());

        Path staged;
        try {
            staged = renderer.renderToTemp(desired, fragmentDir);
        } catch (IOException e) {
            throw new DhcpServiceControlFailedException("dhcpd 조각 임시 파일 생성 실패 : " + e.getMessage());
        }

        SwapResult swap;
        try {
            swap = atomicAssetSwap.swap(fragmentPath, staged, key);
        } catch (IOException e) {
            throw new DhcpServiceControlFailedException("dhcpd 조각 원자 교체 실패 : " + e.getMessage());
        }

        // 게이트 : dhcpd -t 로 조각을 include 한 전체 구성을 검사한다.
        CommandResult gate = commandRunner.run(AllowedCommand.DHCPD_SYNTAX_CHECK, List.of(dhcpdConfPath.toString()));
        if (gate.outcome() == CommandOutcome.COMPLETED && !gate.exitedZero()) {
            // 문법 거절 — 아직 재기동하지 않았으므로 조각만 이전으로 되돌린다(서비스는 이전 구성 그대로).
            restoreFragment(swap, fragmentPath, fragmentDir, key);
            return ApplyOutcome.rejected(joinOutput(gate));
        }
        if (gate.outcome() != CommandOutcome.COMPLETED) {
            // 게이트 자체가 실행 불능(바이너리 부재·타임아웃) — 판정할 수 없어 이전으로 되돌린다(500).
            restoreFragment(swap, fragmentPath, fragmentDir, key);
            return ApplyOutcome.rolledBack("dhcpd 문법 검사(dhcpd -t)를 실행할 수 없습니다 : " + gate.outcome()
                    + ". dhcpd 바이너리·sudoers 구성을 확인하세요.");
        }

        // 재기동 : 게이트 통과 → 새 조각으로 서비스를 재기동한다.
        CommandResult restart = commandRunner.run(AllowedCommand.DHCPD_SERVICE_RESTART, List.of());
        if (!restart.exitedZero()) {
            return restoreAndReverify(swap, fragmentPath, fragmentDir, key);
        }

        // 검증 : 재기동 후 실제로 active 인지 확인한다.
        ServiceState state = serviceInspector.status();
        if (state == ServiceState.ACTIVE) {
            return ApplyOutcome.applied(swap.archivedVersionId().orElse(null));
        }
        return restoreAndReverify(swap, fragmentPath, fragmentDir, key);
    }

    /** dhcpd -t 원문 — stdout·stderr 를 각각 트림해 개행으로 합친다(무구분자 병합으로 마지막 줄이 붙는 것을 막는다). */
    private static String joinOutput(CommandResult r) {
        return (r.stdout().strip() + "\n" + r.stderr().strip()).strip();
    }

    /**
     * 복원 경로 — 이전 조각으로 되돌린 뒤 재기동 1회 + 검증 1회(재귀 없음). 이전 구성으로 살아나면 ROLLED_BACK(500,
     * 사용자 desired 는 반영 안 됨), 그마저 실패해 비활성이면 RESTORE_FAILED(500, 수동 복구 안내).
     */
    private ApplyOutcome restoreAndReverify(SwapResult swap, Path fragmentPath, Path fragmentDir, AssetVersionKey key) {
        restoreFragment(swap, fragmentPath, fragmentDir, key);
        commandRunner.run(AllowedCommand.DHCPD_SERVICE_RESTART, List.of());
        ServiceState state = serviceInspector.status();
        if (state == ServiceState.ACTIVE) {
            return ApplyOutcome.rolledBack(
                    "dhcpd 재기동에 실패해 이전 구성으로 되돌렸습니다. 서비스는 이전 구성으로 동작 중이며 방금 입력한 구성은 반영되지 않았습니다.");
        }
        return ApplyOutcome.restoreFailed(
                "dhcpd 재기동과 이전 구성 복원이 모두 실패해 서비스가 비활성입니다. 수동 복구가 필요합니다 — "
                        + "이전 조각(" + fragmentPath + ")을 확인한 뒤 `sudo systemctl restart dhcpd` 를 실행하세요.");
    }

    /**
     * 조각을 이전 상태로 되돌린다. 이전본이 있으면 그 바이트로 원자 복원하고, 최초 적용이라 이전본이 없으면
     * 유효한 빈 조각을 쓴다(삭제 아님 — include 는 성립해야 한다, D7). 복원 IO 실패는 로그만 하고 삼킨다 —
     * 파이프라인은 예외로 끊지 않으며 후속 검증이 실제 서비스 상태를 반영한다.
     */
    private void restoreFragment(SwapResult swap, Path fragmentPath, Path fragmentDir, AssetVersionKey key) {
        boolean restoredFromArchive = false;
        try {
            if (swap.archivedPath().isPresent()) {
                atomicAssetSwap.restore(fragmentPath, swap.archivedPath().get());
                restoredFromArchive = true;
            } else {
                writeEmptyFragment(fragmentPath, fragmentDir);
            }
        } catch (IOException e) {
            log.error("[pxe-network] dhcpd 조각 복원 실패 — fragment={}", fragmentPath, e);
        }
        if (restoredFromArchive) {
            // 이전 조각으로 되돌아왔으므로 방금 스왑이 만든 archive 는 이제 현재 조각과 동일하다 — 이력에서
            // 제거해 "이력 = 현재 아닌 되돌릴 수 있는 버전" 불변을 지킨다(연속 실패가 정당한 이전 버전을 보존
            // 한도 밖으로 밀어내는 것을 막는다). 진단 롤백의 remove 승격 패턴과 동형.
            swap.archivedVersionId().ifPresent(id -> historyService.remove(key, id));
        }
    }

    /** 머리말만 있는 유효한 빈 조각을 원자적으로 쓴다(조각 디렉토리 temp → ATOMIC_MOVE → 기본 권한). */
    private void writeEmptyFragment(Path fragmentPath, Path fragmentDir) throws IOException {
        Path temp = fragmentDir.resolve(fragmentPath.getFileName() + ".empty-" + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(temp, renderer.renderEmpty());
            Files.move(temp, fragmentPath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        // 정상 스왑·복원(AtomicAssetSwap)과 동일하게 빈 조각도 기본 권한으로 하드닝한다(권한 일관성).
        fileSystemHardener.applyDefaultPermissionsForFile(fragmentPath);
    }
}

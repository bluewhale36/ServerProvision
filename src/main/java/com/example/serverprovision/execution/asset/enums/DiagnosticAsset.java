package com.example.serverprovision.execution.asset.enums;

import com.example.serverprovision.global.marker.MarkerLayout;

import java.nio.file.Path;

/**
 * 진단 리눅스 부팅에 쓰이는 자산 6종의 고정 슬롯(E1-R 포인트 3 확정). 개수·구성이 고정이라
 * DB 열거가 불필요하므로 자원 엔티티가 아니라 enum 이 SSOT 다(DEC-14 — MA 자원 도메인 편입 탈락).
 * 각 파일명은 {@code scripts/diag-image/build-assets.sh} 산출물과 1:1 이며, 그 스크립트가 조립 SSOT 다.
 *
 * <p>파일명이 컴파일 상수라 사용자 입력 경로가 개입할 여지가 없다 — 슬롯을 URL 경로 변수로 노출하지
 * 않으므로 path traversal 표면이 0 이고, forging(404) 경로도 성립하지 않는다.</p>
 *
 * <p>마커 레이아웃: 단일 파일 5종은 {@link MarkerLayout#SIDECAR}(형제 {@code <name>.provision.json}),
 * 디렉토리인 부분 apk 저장소 1종은 {@link MarkerLayout#IN_TREE}({@code repo/.provision.json}).</p>
 *
 * <p>{@code replaceable}(E1-I-2-a): UI 업로드 교체 대상 여부. 단일 파일 4종은 교체 가능하나, 조립 자산
 * apkovl(서명 공개키 동봉 tar) · repo(서명된 저장소 디렉토리)는 내부 구조·서명 검증이 불가해 임의 업로드
 * 교체 대상에서 뺀다(빌드 스크립트 소관). layout 으로 유도할 수 없어(apkovl 은 SIDECAR 이나 비대상) 명시 속성.</p>
 */
public enum DiagnosticAsset {

    VMLINUZ  ("vmlinuz-lts",        MarkerLayout.SIDECAR, "커널 (vmlinuz-lts)",             Category.NETBOOT, "Alpine 버전 업그레이드 시", true),
    INITRAMFS("initramfs-lts",      MarkerLayout.SIDECAR, "초기 램디스크 (initramfs-lts)",  Category.NETBOOT, "Alpine 버전 업그레이드 시", true),
    MODLOOP  ("modloop-lts",        MarkerLayout.SIDECAR, "커널 모듈 (modloop-lts)",        Category.NETBOOT, "Alpine 버전 업그레이드 시", true),
    APKOVL   ("diag.apkovl.tar.gz", MarkerLayout.SIDECAR, "설정 가방 (diag.apkovl.tar.gz)", Category.CONFIG,  "부팅 구성 변경 시",       false),
    REPO     ("repo",               MarkerLayout.IN_TREE, "부분 apk 저장소 (repo/)",         Category.CONFIG,  "패키지 구성 변경 시",       false),
    AGENT    ("agent.sh",           MarkerLayout.SIDECAR, "진단 에이전트 (agent.sh)",        Category.AGENT,   "에이전트 수정 시",         true);

    private final String filename;
    private final MarkerLayout layout;
    private final String label;
    private final Category category;
    private final String replaceCadence;
    private final boolean replaceable;

    DiagnosticAsset(String filename, MarkerLayout layout, String label, Category category,
                    String replaceCadence, boolean replaceable) {
        this.filename = filename;
        this.layout = layout;
        this.label = label;
        this.category = category;
        this.replaceCadence = replaceCadence;
        this.replaceable = replaceable;
    }

    /** 자산 서빙 루트 아래의 실제 경로. 파일명이 상수라 traversal 불가. */
    public Path resolve(Path assetsRoot) {
        return assetsRoot.resolve(filename);
    }

    public String filename() {
        return filename;
    }

    public MarkerLayout layout() {
        return layout;
    }

    public String label() {
        return label;
    }

    public Category category() {
        return category;
    }

    public String replaceCadence() {
        return replaceCadence;
    }

    /** UI 업로드 교체 대상 여부(E1-I-2-a). 조립 자산(apkovl · repo)은 false. */
    public boolean replaceable() {
        return replaceable;
    }
}

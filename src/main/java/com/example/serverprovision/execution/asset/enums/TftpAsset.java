package com.example.serverprovision.execution.asset.enums;

import com.example.serverprovision.global.marker.MarkerLayout;

import java.nio.file.Path;

/**
 * TFTP/PXE 부팅 인프라 자산의 고정 슬롯. 현재는 iPXE UEFI 부트로더 {@code ipxe.efi} 1종이다. 진단 자산과
 * 마찬가지로 개수·구성이 고정이라 DB 열거가 불필요하므로 자원 엔티티가 아니라 enum 이 SSOT 다(DEC-14 동형).
 *
 * <p><b>읽기·봉인 전용</b>: {@code ipxe.efi} 는 {@code dnf install ipxe-bootimgs} 가 설치한 배포판 산출물
 * ({@code /usr/share/ipxe/ipxe-x86_64.efi})을 복사한 것이라, 갱신 주기가 패키지 업데이트에 묶여 있고 갱신
 * 절차는 "복사 후 재봉인" 이다. 그래서 UI 업로드 교체 대상이 아니며({@link DiagnosticAsset} 의
 * {@code replaceable} 개념 자체가 없다) 대시보드 조회 + 영역 단위 봉인({@code POST /system/asset/TFTP/seal})
 * 만 갖는다 — 업로드 폼이 없을 뿐 갱신 경로는 완결된다.</p>
 *
 * <p>마커 레이아웃은 단일 파일이므로 {@link MarkerLayout#SIDECAR}(형제 {@code ipxe.efi.provision.json}).</p>
 */
public enum TftpAsset {

    IPXE_EFI("ipxe.efi", MarkerLayout.SIDECAR, "iPXE 부트 (ipxe.efi)", Category.NETBOOT, "패키지 업데이트 시");

    private final String filename;
    private final MarkerLayout layout;
    private final String label;
    private final Category category;
    private final String replaceCadence;

    TftpAsset(String filename, MarkerLayout layout, String label, Category category, String replaceCadence) {
        this.filename = filename;
        this.layout = layout;
        this.label = label;
        this.category = category;
        this.replaceCadence = replaceCadence;
    }

    /** 자산 서빙 루트(TFTP root) 아래의 실제 경로. 파일명이 상수라 traversal 불가. */
    public Path resolve(Path tftpRoot) {
        return tftpRoot.resolve(filename);
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
}

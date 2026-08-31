package com.example.serverprovision.execution.engine.raid;

/**
 * RAID 카드 칩 계열(E3.5-1) — 제어 계열(드라이버 · CLI · 파서)을 정하는 축이다(사전 조사 §2).
 * 브랜드가 아니라 PCI Vendor:Device 가 판별자다: {@code 1000:0097} = SAS3008(IR) · {@code 1000:005d} = SAS3108(MegaRAID).
 */
public enum RaidChipFamily {

    /** Fusion-MPT SAS-3 의 Integrated RAID 펌웨어 — sas3ircu 계열 (예: GIGABYTE CRA3338). */
    MPT_IR,

    /** MegaRAID RAID-on-Chip — storcli 계열 (예: AVAGO MegaRAID 9361-8i). */
    MEGARAID
}

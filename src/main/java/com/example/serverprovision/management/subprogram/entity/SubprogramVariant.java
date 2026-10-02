package com.example.serverprovision.management.subprogram.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.management.subprogram.enums.InstallEntrypointKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * 벤더 패키지 안의 버전별 변형(R15-1 D-2) — "이 OS 버전에는 이 진입점을 이렇게 실행한다" 한 행.
 * {@code osVersion} 이 null 이면 자원의 OS 전 버전에 적용하는 와일드카드(자원당 1 행). 설치 방식은
 * 진입점 확장자가 정한다({@link InstallEntrypointKind}). 자원 단위 진입점은 이 엔티티로 옮겨 SSOT 를 하나로 둔다.
 */
@Entity
@Table(name = "subprogram_variant",
        uniqueConstraints = @UniqueConstraint(name = "uk_subprogram_variant_entry",
                columnNames = {"subprogram_id", "os_version", "entrypoint_relative_path"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubprogramVariant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subprogram_id", nullable = false)
    private Subprogram subprogram;

    @Column(name = "os_version", length = 64)
    private String osVersion;

    @Column(name = "entrypoint_relative_path", nullable = false, length = 512)
    private String entrypointRelativePath;

    @Column(name = "arguments", length = 512)
    private String arguments;

    @Column(name = "reboot_required", nullable = false)
    private boolean rebootRequired;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public SubprogramVariant(Subprogram subprogram, String osVersion, String entrypointRelativePath,
                             String arguments, boolean rebootRequired, int sortOrder) {
        this.subprogram = subprogram;
        this.osVersion = blankToNull(osVersion);
        this.entrypointRelativePath = entrypointRelativePath;
        this.arguments = blankToNull(arguments);
        this.rebootRequired = rebootRequired;
        this.sortOrder = sortOrder;
    }

    /** 같은 동기화 키(버전 + 진입점)의 행을 제자리에서 갱신(id 유지) — 인자 · 재부팅 · 순서 · 표기(대소문자 · 공백)는 새 입력을 따른다. */
    public void updateFrom(SubprogramVariant d) {
        this.osVersion = d.osVersion;
        this.entrypointRelativePath = d.entrypointRelativePath;
        this.arguments = d.arguments;
        this.rebootRequired = d.rebootRequired;
        this.sortOrder = d.sortOrder;
    }

    /** 버전 비교 키 — 중복 판정과 R15-2 의 선택 비교가 같은 정규화(trim · 대소문자 무시)를 쓴다. null = 전 버전. */
    public String versionKey() {
        return versionKeyOf(osVersion);
    }

    public static String versionKeyOf(String osVersion) {
        String v = blankToNull(osVersion);
        return v == null ? null : v.toLowerCase(Locale.ROOT);
    }

    /** 진입점 비교 키(HF23) — 역슬래시 · 끝 슬래시 · 대소문자 차이를 무시한다. 한 버전에 여러 행이 생기면서 중복 판정의 축이 됐다. */
    public static String entrypointKeyOf(String entrypoint) {
        String e = blankToNull(entrypoint);
        if (e == null) {
            return null;
        }
        e = e.replace('\\', '/');
        while (e.endsWith("/")) {
            e = e.substring(0, e.length() - 1);
        }
        return e.toLowerCase(Locale.ROOT);
    }

    /** 동기화 키(HF23) — 한 OS 버전에 여러 행(폴더 여럿)이 생기므로 버전만으로는 행을 가를 수 없다. */
    public String syncKey() {
        return versionKey() + "|" + entrypointKeyOf(entrypointRelativePath);
    }

    public boolean appliesToAllVersions() {
        return osVersion == null;
    }

    public InstallEntrypointKind entrypointKind() {
        return InstallEntrypointKind.fromPath(entrypointRelativePath).orElseThrow();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

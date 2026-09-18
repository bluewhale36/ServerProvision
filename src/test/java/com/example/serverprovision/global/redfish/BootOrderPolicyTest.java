package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HF20 CP4 — 정착 정책. 실측 규칙(POST 때 BootOrder 첫 항목의 그룹이 Fixed Boot Order 1순위)을 전제로,
 * 두 정책이 첫 항목을 어디에 두는지와 idempotent(같으면 empty)를 고정한다.
 */
class BootOrderPolicyTest {

    private static BootEntries.Entry e(String id, BootEntries.Kind kind, String name) {
        return new BootEntries.Entry(id, name, "", kind);
    }

    static final List<BootEntries.Entry> ENTRIES = List.of(
            e("Boot0001", BootEntries.Kind.SHELL, "UEFI: Built-in EFI Shell"),
            e("Boot0002", BootEntries.Kind.DISK, "Windows Boot Manager (LSI Logical Volume 3000, Partition 1)"),
            e("Boot0003", BootEntries.Kind.NETWORK, "UEFI: PXE IPv4 Intel(R) I350"),
            e("Boot0004", BootEntries.Kind.DISK, "UEFI: SanDisk Cruzer, Partition 1"),
            e("Boot0005", BootEntries.Kind.OTHER, "Something Odd"));

    @Test
    @DisplayName("SHELL_LAST — 셸만 맨 뒤로, 나머지 상대 순서 보존 · Windows 설치 직후(셸 첫 항목) 결함 상태를 고친다")
    void shellLast() {
        Optional<List<String>> out = BootOrderPolicy.SHELL_LAST.reorder(
                List.of("Boot0001", "Boot0003", "Boot0002", "Boot0004"), ENTRIES);
        assertThat(out).contains(List.of("Boot0003", "Boot0002", "Boot0004", "Boot0001"));
    }

    @Test
    @DisplayName("SHELL_LAST — 이미 맨 뒤면 empty(PATCH 없음) · 셸 항목이 없어도 empty")
    void shellLastIdempotent() {
        assertThat(BootOrderPolicy.SHELL_LAST.reorder(List.of("Boot0003", "Boot0002", "Boot0001"), ENTRIES)).isEmpty();
        assertThat(BootOrderPolicy.SHELL_LAST.reorder(List.of("Boot0003", "Boot0002"), ENTRIES)).isEmpty();
        assertThat(BootOrderPolicy.SHELL_LAST.reorder(List.of(), ENTRIES)).isEmpty();
        assertThat(BootOrderPolicy.SHELL_LAST.reorder(null, ENTRIES)).isEmpty();
    }

    @Test
    @DisplayName("DISK_FIRST — Windows Boot Manager → 다른 디스크 → 나머지(네트워크 · 기타) → 셸 맨 뒤")
    void diskFirst() {
        Optional<List<String>> out = BootOrderPolicy.DISK_FIRST.reorder(
                List.of("Boot0003", "Boot0005", "Boot0004", "Boot0001", "Boot0002"), ENTRIES);
        assertThat(out).contains(List.of("Boot0002", "Boot0004", "Boot0003", "Boot0005", "Boot0001"));
    }

    @Test
    @DisplayName("DISK_FIRST — 이미 그 순서면 empty · 목록에 없는 id 는 OTHER 로 두고 자리를 지킨다 · '0002' 꼴 id 도 정규화해 비교")
    void diskFirstIdempotentAndUnknownIds() {
        assertThat(BootOrderPolicy.DISK_FIRST.reorder(List.of("Boot0002", "Boot0004", "Boot0003", "Boot0005", "Boot0001"), ENTRIES)).isEmpty();
        assertThat(BootOrderPolicy.DISK_FIRST.reorder(List.of("Boot0009", "Boot0002"), ENTRIES))
                .contains(List.of("Boot0002", "Boot0009"));
        assertThat(BootOrderPolicy.DISK_FIRST.reorder(List.of("0002", "0001"), ENTRIES)).isEmpty();
        assertThat(BootOrderPolicy.SHELL_LAST.reorder(List.of("0001", "0002"), ENTRIES)).contains(List.of("Boot0002", "Boot0001"));
    }

    @Test
    @DisplayName("라벨은 사람이 읽는 정책 이름 — 로그 · 결과 메시지에 그대로 쓰인다")
    void labels() {
        assertThat(BootOrderPolicy.SHELL_LAST.label()).isEqualTo("셸을 맨 뒤로");
        assertThat(BootOrderPolicy.DISK_FIRST.label()).isEqualTo("디스크를 맨 앞으로");
    }
}

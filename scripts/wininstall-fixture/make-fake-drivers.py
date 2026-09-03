#!/usr/bin/env python3
"""가짜 Windows 드라이버 트리 생성(E4-1-a-4 샌드박스 · CP5) — Subprogram(DRIVER) 등록 입력용.

셋을 만든다: chipset(INF 2 + 하위 폴더) · qat(INF 1) · lan-tool(INF 0 — 유틸 성격, 조립에서 제외돼야 한다).
관리 화면 Subprogram 등록(kind=DRIVER · 공용)에 각 디렉토리를 트리 루트로 넣은 뒤, 대시보드 [드라이버 페이로드 조립] 을 누르면
sources/$OEM$/$1/SPV/Drivers/<id>_<슬러그>/ 에 chipset · qat 만 복사되고 lan-tool 은 chip 의 '제외' 에 잡힌다.

사용: python3 make-fake-drivers.py <대상 루트>
예 : python3 scripts/wininstall-fixture/make-fake-drivers.py /tmp/spv-drivers
"""
import pathlib
import sys

INF = "[Version]\r\nSignature=\"$WINDOWS NT$\"\r\nClass=System\r\nProvider=%ServerProvision%\r\nDriverVer=09/03/2026,1.0.0.0\r\n\r\n[Strings]\r\nServerProvision=\"ServerProvision fake driver\"\r\n"


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    root = pathlib.Path(sys.argv[1])
    chipset = root / "chipset-fake-1.0"
    (chipset / "x64").mkdir(parents=True, exist_ok=True)
    (chipset / "chipset.inf").write_text(INF, encoding="ascii")
    (chipset / "chipset.sys").write_bytes(b"MZ" + b"\0" * 4094)
    (chipset / "x64" / "chipset-x64.inf").write_text(INF, encoding="ascii")
    (chipset / "x64" / "chipset-x64.cat").write_bytes(b"\0" * 512)
    qat = root / "qat-fake-2.1"
    qat.mkdir(parents=True, exist_ok=True)
    (qat / "qat.inf").write_text(INF, encoding="ascii")
    (qat / "qat.sys").write_bytes(b"MZ" + b"\0" * 2046)
    lan = root / "lan-tool-fake-0.9"
    lan.mkdir(parents=True, exist_ok=True)
    (lan / "lantool.exe").write_bytes(b"MZ" + b"\0" * 1022)
    (lan / "readme.txt").write_text("no INF here - utility, must be excluded (INF_MISSING)\n", encoding="ascii")
    for d in (chipset, qat, lan):
        print(f"드라이버 트리 생성: {d}")


if __name__ == "__main__":
    main()

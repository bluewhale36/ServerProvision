#!/usr/bin/env python3
"""가짜 Windows 설치 소스 루트 생성 — 샌드박스(CP5) · 수동 확인용.

앱은 <루트>/sources/install.wim 의 WIM 헤더(208 B)와 XML 자원만 읽으므로, 실제 4.8 GB 파일 대신 헤더 + XML 만 있는
가짜 WIM 으로 폼 · 대시보드 · 검사기를 그대로 검증할 수 있다. boot.wim · setup.exe 는 존재만 보므로 빈 파일로 둔다.

사용: python3 make-fake-source.py <대상 루트> [xml 파일 = 같은 디렉토리의 install.wim.xml]
예 : python3 scripts/wininstall-fixture/make-fake-source.py /tmp/spv-win2025
     WINDOWS_INSTALL_SOURCE_ROOT=/tmp/spv-win2025 ... ./gradlew bootRun
"""
import pathlib
import struct
import sys

HEADER_SIZE = 208
MAGIC = b"MSWIM\x00\x00\x00"
WIM_VERSION = 0x10D00
WIM_FLAGS = 0x40082          # 실측 install.wim 의 값(의미는 앱이 읽지 않는다)
COMPRESSION_SIZE = 32768
RESHDR_FLAG_METADATA = 0x2   # XML 자원 플래그(비압축)


def build_fake_wim(xml_text: str) -> bytes:
    payload = "﻿".encode("utf-16-le") + xml_text.encode("utf-16-le")
    header = bytearray(HEADER_SIZE)
    header[0:8] = MAGIC
    struct.pack_into("<IIII", header, 8, HEADER_SIZE, WIM_VERSION, WIM_FLAGS, COMPRESSION_SIZE)
    struct.pack_into("<I", header, 44, xml_text.count("<IMAGE INDEX="))
    size_and_flags = len(payload) | (RESHDR_FLAG_METADATA << 56)
    struct.pack_into("<QQQ", header, 72, size_and_flags, HEADER_SIZE, len(payload))
    return bytes(header) + payload


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    root = pathlib.Path(sys.argv[1])
    xml_path = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else pathlib.Path(__file__).with_name("install.wim.xml")
    xml_text = xml_path.read_text(encoding="utf-8")
    sources = root / "sources"
    sources.mkdir(parents=True, exist_ok=True)
    (sources / "install.wim").write_bytes(build_fake_wim(xml_text))
    (sources / "boot.wim").write_bytes(b"FAKE-BOOT-WIM\n")
    (sources / "setup.exe").write_bytes(b"MZ")
    print(f"가짜 설치 소스 생성: {root}  (install.wim {len(build_fake_wim(xml_text))} B · 이미지 {xml_text.count('<IMAGE INDEX=')}종)")


if __name__ == "__main__":
    main()

#!/bin/sh
# 진단 리눅스 ACPI 이벤트 처리(HF15-4 · 실기 3호 F-10) — BMC 의 정상 종료(Redfish GracefulShutdown)는 ACPI 전원 버튼을
# 누르는 것이라 이 핸들러가 없으면 진단 리눅스는 응답하지 않는다. 전원 버튼 → poweroff 만 처리한다.
case "$1" in
    button/power)
        logger -t provision-acpi "power button — poweroff" 2>/dev/null
        /sbin/poweroff ;;
    *) : ;;
esac

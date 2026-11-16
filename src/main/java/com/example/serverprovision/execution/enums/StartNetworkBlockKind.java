package com.example.serverprovision.execution.enums;

/** 개시를 막는 네트워크 사유. 선언 순서 = 화면 표시 순서. 주소 뒤에 조사가 오지 않게 쓴다(숫자 읽기에 따라 가/이 가 갈린다). */
public enum StartNetworkBlockKind {

    RANGE_UNSET {
        @Override
        public String message(String observed, String allowedCidr) {
            return "프로비저닝 허용 대역이 설정되지 않아 주소를 확인할 수 없습니다. 시스템 설정에서 프로비저닝 허용 대역을 먼저 저장해야 합니다.";
        }
    },
    BMC_UNKNOWN {
        @Override
        public String message(String observed, String allowedCidr) {
            return "BMC 주소를 아직 알 수 없습니다. 하드웨어 수집이 끝나 BMC 주소가 보고된 뒤 개시할 수 있습니다.";
        }
    },
    BMC_OUTSIDE {
        @Override
        public String message(String observed, String allowedCidr) {
            return "BMC 주소가 프로비저닝 허용 대역 밖에 있습니다(BMC " + observed + ", 허용 대역 " + allowedCidr
                    + "). BMC 가 프로비저닝 네트워크에 연결돼 있는지 확인해야 합니다.";
        }
    },
    LAN_UNKNOWN {
        @Override
        public String message(String observed, String allowedCidr) {
            return "LAN 주소를 아직 알 수 없습니다. 서버가 PXE 부팅으로 주소를 보고한 뒤 개시할 수 있습니다.";
        }
    },
    LAN_OUTSIDE {
        @Override
        public String message(String observed, String allowedCidr) {
            return "LAN 주소가 프로비저닝 허용 대역 밖에 있습니다(LAN " + observed + ", 허용 대역 " + allowedCidr
                    + "). 서버의 LAN 이 프로비저닝 네트워크에 연결돼 있는지 확인해야 합니다.";
        }
    };

    public abstract String message(String observed, String allowedCidr);
}

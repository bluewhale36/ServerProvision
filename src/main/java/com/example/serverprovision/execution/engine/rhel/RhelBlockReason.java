package com.example.serverprovision.execution.engine.rhel;

/** RHEL 정의서를 kickstart 로 옮길 수 없는 사유. 문구는 화면에 나가므로 DESIGN.md 7절 문체를 따른다. */
public enum RhelBlockReason {

    OS_METADATA_MISSING {
        @Override
        public String message(String detail) {
            return "정의서의 설치 OS(id " + detail + ")를 찾을 수 없습니다.";
        }
    },
    UNSUPPORTED_RELEASE {
        @Override
        public String message(String detail) {
            return detail + " 는 설치할 수 없습니다. Rocky Linux 8, 9, 10 만 설치할 수 있습니다.";
        }
    },
    ENVIRONMENT_MISSING {
        @Override
        public String message(String detail) {
            return "설치 환경(id " + detail + ")을 카탈로그에서 찾을 수 없습니다.";
        }
    },
    PACKAGE_GROUP_MISSING {
        @Override
        public String message(String detail) {
            return "추가 패키지 그룹(id " + detail + ")을 카탈로그에서 찾을 수 없습니다.";
        }
    };

    public abstract String message(String detail);
}

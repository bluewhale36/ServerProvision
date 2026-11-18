package com.example.serverprovision.management.os.enums;

import com.example.serverprovision.management.os.vo.OsMajorVersion;

/**
 * OS 버전 칸을 채우는 규칙(S23-1). 등록 화면이 미리 채울 값과 서버가 받아들일 값을 같은 상수에서 읽는다.
 */
public enum OSVersionRule {

	/** 메이저로 시작하는 '메이저.세부' 버전이다. 화면은 "9." 을 미리 채운다. */
	MAJOR_PREFIX {
		@Override
		public boolean accepts(OsMajorVersion major, String version) {
			return major != null && major.leads(version);
		}

		@Override
		public String prefill(OsMajorVersion major) {
			return major + ".";
		}
	},

	/** 메이저 그대로가 버전이다. 화면은 "2025" 를 채우고 고칠 수 없게 둔다. */
	EXACT_MAJOR {
		@Override
		public boolean accepts(OsMajorVersion major, String version) {
			return major != null && major.isExactly(version);
		}

		@Override
		public String prefill(OsMajorVersion major) {
			return major.toString();
		}

		@Override
		public boolean locked() {
			return true;
		}
	},

	/** 형식을 정하지 않는다. */
	FREE {
		@Override
		public boolean accepts(OsMajorVersion major, String version) {
			return true;
		}

		@Override
		public String prefill(OsMajorVersion major) {
			return "";
		}
	};

	/** 이 규칙에서 받아들이는 버전인가. */
	public abstract boolean accepts(OsMajorVersion major, String version);

	/** 등록 화면 버전 칸에 미리 채울 값. */
	public abstract String prefill(OsMajorVersion major);

	/** 미리 채운 값을 고칠 수 없게 두는가. */
	public boolean locked() {
		return false;
	}
}

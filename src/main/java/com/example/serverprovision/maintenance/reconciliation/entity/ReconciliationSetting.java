package com.example.serverprovision.maintenance.reconciliation.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.maintenance.reconciliation.enums.ReconciliationSettingItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MK4-3-1 — 운영 설정 한 항목의 저장 행. <b>항목 하나가 행 하나</b>다.
 *
 * <p>고정 컬럼 단일행 대신 이 모양을 택한 이유는 확장 비용이다(D5 개정). 컬럼으로 두면 설정을 하나
 * 늘릴 때마다 스키마를 바꿔야 하고, 이 환경에서는 애플리케이션 계정에 변경 권한이 없어 그때마다
 * 사람이 개입해야 한다. 설정 항목은 운영하면서 계속 늘어나는 자리라 그 마찰이 실재한다.</p>
 *
 * <p>키-값 저장의 약점은 보통 "키가 무엇인지 아무도 모른다" 는 것인데, 여기서는 항목 카탈로그가
 * {@link ReconciliationSettingItem} 으로 코드에 있어 그 약점이 성립하지 않는다. 그 열거형을 그대로
 * <b>기본키</b>로 쓰므로 오타가 들어갈 수 없고, 한 항목이 두 행을 가질 수 없다는 것도 데이터베이스가
 * 지킨다.</p>
 *
 * <p>대신 값의 타입과 필수 여부는 데이터베이스가 지켜 주지 못한다. 그 자리는 항목 카탈로그의
 * 값 타입과 요청 검증({@code @AssertTrue})이 맡는다 — 원래도 범위 · 형식 검증은 그쪽이 하고 있었다.</p>
 */
@Entity
@Table(name = "reconciliation_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationSetting extends BaseTimeEntity {

	/** 항목이 곧 신원이다. 열거형 이름을 그대로 저장해 사람이 읽을 수 있게 둔다. */
	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "item", nullable = false, length = 64)
	private ReconciliationSettingItem item;

	/**
	 * 저장된 값의 원문. 형태는 항목의 값 타입이 정한다 — 참거짓은 {@code true}/{@code false},
	 * 정수는 십진수, 종류 목록은 콤마 구분, 경로 목록은 줄바꿈 구분이다.
	 *
	 * <p>목록 둘의 구분자가 다른 것은 값의 성질이 다르기 때문이다. 종류 이름은 콤마를 품을 수 없어
	 * 콤마로 잇고, 파일 경로는 콤마를 품을 수 있어 줄바꿈으로 잇는다. 한쪽으로 통일하면 경로가
	 * 깨지거나 종류 목록이 이유 없이 여러 줄이 된다.</p>
	 */
	@Column(name = "value", nullable = false, length = 4096)
	private String value;

	private ReconciliationSetting(ReconciliationSettingItem item, String value) {
		this.item = item;
		this.value = value;
	}

	public static ReconciliationSetting of(ReconciliationSettingItem item, String value) {
		return new ReconciliationSetting(item, value == null ? "" : value);
	}

	public void changeValue(String value) {
		this.value = value == null ? "" : value;
	}
}

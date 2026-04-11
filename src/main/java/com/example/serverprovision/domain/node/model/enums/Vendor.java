package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서버 메인보드 제조사를 나타내는 열거형이다.
 *
 * <p>역할: {@code BoardModel#vendor} 필드의 값 도메인을 정의하며, DB에 문자열로 저장된다.
 * PXE 부팅 요청의 {@code vendor} 파라미터를 파싱할 때와 {@code BoardModelRepository#findByVendorAndModelName}
 * 조회 시 사용된다.</p>
 *
 * <p>유스케이스: {@code ServerNodeService#getOrRegisterNode}가 {@code Vendor.valueOf(vendorStr)}로
 * 파라미터를 변환하여 보드 모델을 조회한다. {@code getVendorByString}은 대소문자 무관 검색을
 * 지원하여 PXE 부팅 클라이언트가 보내는 다양한 표기 방식을 처리한다.</p>
 *
 * <p>확장 가이드: 새 제조사를 지원하려면 이 열거형에 상수를 추가하고, 해당 제조사의
 * {@code BoardModel} 레코드를 DB에 삽입한다. {@code getVendorByString}은 모든 값에 자동으로
 * 적용되므로 별도 수정이 필요 없다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum Vendor {
    GIGABYTE("Gigabyte"),
    ASUS("Asus");

    /** 내부 표시명. {@code getVendorByString}에서 대소문자 무관 비교에 사용된다. */
    private final String vendorNameInternal;

    /**
     * 문자열로 {@code Vendor}를 조회한다. 열거형 이름({@code GIGABYTE}) 또는
     * 내부 표시명({@code Gigabyte}) 모두 대소문자 무관하게 매칭된다.
     *
     * @param vendorName 조회할 제조사 문자열
     * @return 매칭되는 {@code Vendor}, 없으면 {@code null}
     */
    public static Vendor getVendorByString(String vendorName) {
        for (Vendor vendor : Vendor.values()) {
            if (vendor.getVendorNameInternal().equalsIgnoreCase(vendorName) || vendor.name().equalsIgnoreCase(vendorName)) {
                return vendor;
            }
        }
        return null;
    }
}

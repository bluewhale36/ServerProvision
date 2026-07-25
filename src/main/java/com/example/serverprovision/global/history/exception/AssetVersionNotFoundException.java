package com.example.serverprovision.global.history.exception;

import com.example.serverprovision.global.exception.NotFoundException;
import com.example.serverprovision.global.history.AssetVersionKey;

/**
 * {@code openVersion} 이 요청한 버전 id 가 없거나 그 버전이 요청 키 {@code (category, name)} 소속이 아닌 경우.
 * b-1 은 저장소 능력만 두고 HTTP 노출은 없으나, 롤백 UI(b-2)가 이 예외를 404 로 매핑해 소비한다
 * (base {@link NotFoundException}). 소유권 검증 실패는 다른 자산의 버전을 되살리는 forging 을 차단한다.
 */
public class AssetVersionNotFoundException extends NotFoundException {

    private AssetVersionNotFoundException(String message) {
        super(message);
    }

    public static AssetVersionNotFoundException of(AssetVersionKey key, Long versionId) {
        return new AssetVersionNotFoundException(
                "버전을 찾을 수 없습니다 : id=" + versionId + ", key=" + key.category() + "/" + key.name());
    }
}

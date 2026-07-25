package com.example.serverprovision.global.history;

import com.example.serverprovision.global.history.entity.AssetVersion;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 도메인 무관 파일 버전 저장소(E1-I-2-b-1). 임의 파일의 이전 버전을 키로 보존·조회·정리한다 — 자산이
 * 진단 리눅스든 PXE 인프라(E1-I-3)든 키만 다르게 그대로 재사용한다.
 *
 * <p>이 슬라이스(b-1)는 저장소 능력까지다. 복원된 버전을 실제로 활성화(스왑·재봉인)하는 도메인 특화 배선과
 * 목록·롤백 UI 는 b-2 소관이다. b-2 는 {@link #openVersion}으로 버전 바이트 경로를 받아 활성화한다.</p>
 */
public interface AssetHistoryService {

    /**
     * 현재 파일을 새 버전으로 아카이브한다(교체 앞에 호출). 파일을 이력 루트로 복사·기록하고 보존 상한을
     * 넘으면 정리한다. 현재 파일이 없으면(최초 생성) 아카이브할 옛 버전이 없으므로 {@link Optional#empty()}.
     *
     * @throws com.example.serverprovision.global.history.exception.AssetVersionArchiveFailedException 복사·해시 IO 실패
     */
    Optional<AssetVersion> archive(AssetVersionKey key, Path currentFile);

    /** 한 자산의 버전을 최신 순으로. */
    List<AssetVersion> list(AssetVersionKey key);

    /**
     * 한 버전의 이력 파일 절대 경로(복원용). 버전이 없거나 그 키 소속이 아니면 예외.
     *
     * @throws com.example.serverprovision.global.history.exception.AssetVersionNotFoundException 없음/소유권 불일치
     */
    Path openVersion(AssetVersionKey key, Long versionId);

    /** 한 자산의 버전을 최근 N개만 남기고 오래된 것부터(파일 + 행) 삭제한다. */
    void prune(AssetVersionKey key);

    /**
     * 한 버전을 이력에서 제거한다(파일 + 행). <b>멱등</b> — 없거나 그 키 소속이 아니면 no-op. 롤백(E1-I-2-b-2)이
     * 되살린 버전을 이력에서 빼 "이력 = 현재 활성본이 아닌 되돌릴 수 있는 버전" 불변을 유지하는 데 쓴다.
     */
    void remove(AssetVersionKey key, Long versionId);
}

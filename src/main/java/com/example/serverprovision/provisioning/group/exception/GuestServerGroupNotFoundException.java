package com.example.serverprovision.provisioning.group.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 지정한 게스트 서버 그룹이 없을 때 (U3-4).
 *
 * <p>정상 흐름에서는 화면이 존재하는 그룹만 링크하므로 도달하지 않는다. 실제 발생 경로는
 * 지워진 그룹을 열어둔 탭에서 조작하거나 URL 을 직접 두드리는 경우다.</p>
 */
public class GuestServerGroupNotFoundException extends NotFoundException {

    public GuestServerGroupNotFoundException(Long groupId) {
        super("서버 그룹을 찾을 수 없습니다. id=" + groupId);
    }
}

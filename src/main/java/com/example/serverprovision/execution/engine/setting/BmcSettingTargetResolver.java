package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.entity.GuestServerDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 표준값과 감지 보드의 프로파일을 한 벌로 — 주기마다 값싸게 조립되므로 캐시 · 저장이 없다. */
@Component
@RequiredArgsConstructor
public class BmcSettingTargetResolver {

    private final BmcStandardSettings standard;
    private final FanProfileResources fanProfileResources;

    public BmcSettingTarget resolve(GuestServerDetail detail) {
        String boardModelName = detail == null || detail.getBoardModel() == null
                ? null : detail.getBoardModel().getModelName();
        return new BmcSettingTarget(standard, boardModelName, fanProfileResources.forBoard(boardModelName).orElse(null));
    }
}

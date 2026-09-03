package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.engine.windows.WindowsOemPayloadAssembler;
import com.example.serverprovision.global.util.FileSize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 대시보드 Windows 설치 소스 영역의 [드라이버 페이로드 조립] 액션(E4-1-a-4 D-6) — 봉인과 의미가 달라 {@code /{area}/seal}
 * 을 재사용하지 않고 전용 경로를 둔다. 동기 PRG · 결과는 flash 로 알린다. 미설정 · 쓰기 불가는 서비스가 409 로 거절한다
 * (버튼 disabled 가 1차 차단, 같은 판정).
 * <ul>
 *   <li>{@code POST /system/windows-install/oem-sync} → 대시보드 PRG</li>
 * </ul>
 */
@Controller
@RequestMapping("/system/windows-install")
@RequiredArgsConstructor
public class WindowsOemPayloadController {

    private final WindowsOemPayloadAssembler assembler;

    @PostMapping("/oem-sync")
    public String sync(RedirectAttributes redirectAttributes) {
        WindowsOemPayloadAssembler.OemAssemblyResult result = assembler.sync();
        String message = "드라이버 페이로드 조립 완료 — 드라이버 " + result.assembled() + "종 · " + FileSize.format(result.totalBytes())
                + (result.excluded() > 0 ? " · 제외 " + result.excluded() + "건(INF 없음 · 트리 부재)" : "");
        redirectAttributes.addFlashAttribute("flashMessage", message);
        return "redirect:/system/asset";
    }
}

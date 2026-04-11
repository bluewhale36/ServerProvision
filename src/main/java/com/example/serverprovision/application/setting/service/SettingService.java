package com.example.serverprovision.application.setting.service;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.dto.SettingCreateRequest;
import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.application.setting.model.enums.SettingStatus;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.repository.SettingRepository;
import com.example.serverprovision.application.setting.service.resolver.SettingProcessResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주문서(세팅 템플릿) 생성 서비스.
 *
 * <p>요청 DTO 목록을 {@link SettingProcessResolver} 전략 빈들에게 위임해
 * 도메인 모델로 해석한 뒤 {@link ServerSetting} 엔티티를 빌드한다. 각
 * {@link AbstractProcessRequest} 타입별 세부 해석 로직은 개별 resolver 가
 * 책임지므로, 새 프로세스 타입 추가 시 이 서비스는 수정할 필요가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingService {

    private final SettingRepository settingRepository;
    private final List<SettingProcessResolver> resolvers;

    public void save(SettingCreateRequest request) {
        log.info("[SettingService] 세팅 주문서 생성 시작. name={}, 단계 수={}",
                request.name(), request.processList().size());

        // 요청 DTO → 도메인 모델 해석 (polymorphic dispatch via SettingProcessResolver)
        // .sorted() 는 AbstractSettingProcess#compareTo 가 processStep.getOrder() 기준이므로
        // 도메인 불변식(단계 실행 순서 정렬)을 서비스 레이어에서 방어적으로 보장한다.
        List<AbstractSettingProcess> resolvedProcesses = request.processList().stream()
                .map(this::resolveOne)
                .sorted()
                .toList();

        SettingProcess settingProcess = new SettingProcess(resolvedProcesses);
        log.info("[SettingService] SettingProcess 구성 완료. 처리된 단계 수={}", resolvedProcesses.size());

        ServerSetting setting = ServerSetting.builder()
                .name(request.name())
                .settingProcess(settingProcess)
                .status(SettingStatus.PENDING)
                .build();
        log.info("[SettingService] ServerSetting 객체 생성 완료. name={}, status={}",
                setting.getName(), setting.getStatus());

        settingRepository.save(setting);
        log.info("[SettingService] [DB 저장 미구현] 저장할 ServerSetting: {}", setting);
    }

    /**
     * 단일 요청 DTO 를 담당 resolver 로 dispatch 한다. 지원하는 resolver 가 없으면
     * {@link IllegalArgumentException} 을 던진다.
     */
    private AbstractSettingProcess resolveOne(AbstractProcessRequest req) {
        log.info("[SettingService] 프로세스 요청 처리 중. type={}", req.getClass().getSimpleName());
        return resolvers.stream()
                .filter(r -> r.supports(req))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 프로세스 요청 타입입니다: " + req.getClass().getSimpleName()))
                .resolve(req);
    }
}

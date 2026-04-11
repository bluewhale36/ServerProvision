package com.example.serverprovision.application.setting.service;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.dto.SettingCreateRequest;
import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.application.setting.model.enums.SettingStatus;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.repository.SettingRepository;
import com.example.serverprovision.application.setting.service.resolver.SettingProcessResolver;
import com.example.serverprovision.global.exception.FieldValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 세팅 주문서 생성 유스케이스를 오케스트레이션하는 Service이다.
 *
 * <p>역할: {@link SettingCreateRequest}의 {@code processList}를 각 프로세스 타입에 대응하는
 * {@link SettingProcessResolver} 구현체에게 위임하여 {@link AbstractSettingProcess} 도메인
 * 모델로 변환하고, 변환 결과를 {@link com.example.serverprovision.application.setting.model.SettingProcess}로
 * 래핑한 뒤 {@link ServerSetting} 엔티티를 빌드하여 영속화한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.controller.SettingController#createSetting}이
 * {@link #save}를 호출하면, Spring이 주입한 {@code List<SettingProcessResolver>}를
 * 순회하여 각 {@link AbstractProcessRequest}를 담당 resolver에 dispatch한다.
 * Resolver 내부에서 발생한 {@link com.example.serverprovision.global.exception.FieldValidationException}에는
 * {@code processList[i].} 인덱스 프리픽스가 부착되어 Bean Validation 경로 포맷과 일치하게 된다.
 * 변환된 프로세스 목록은 {@link AbstractSettingProcess#compareTo}(즉 {@link com.example.serverprovision.application.setting.model.enums.SettingProcessStep#getOrder} 기준)로
 * 정렬된 뒤 {@link ServerSetting}에 담겨 저장된다.</p>
 *
 * <p>확장 가이드: 새 프로세스 타입을 추가할 때 이 서비스 자체는 수정할 필요가 없다.
 * {@link SettingProcessResolver}를 구현하는 새 {@code @Component}를 등록하면
 * Spring이 {@code List<SettingProcessResolver>}에 자동 포함시킨다.
 * 단, {@link AbstractProcessRequest}와 {@link AbstractSettingProcess}의 Jackson
 * {@code @JsonSubTypes}에도 새 타입을 등록해야 JSON 직렬화/역직렬화가 정상 동작한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingService {

    private final SettingRepository settingRepository;
    private final List<SettingProcessResolver> resolvers;

    /**
     * 요청 DTO 목록을 도메인 모델로 해석하고 {@link ServerSetting} 엔티티를 영속화한다.
     *
     * <p>역할: {@link SettingCreateRequest}를 받아 각 {@link AbstractProcessRequest}를
     * 담당 {@link SettingProcessResolver}에 dispatch하여 {@link AbstractSettingProcess}로 변환하고,
     * 정렬된 결과를 {@link com.example.serverprovision.application.setting.model.SettingProcess}로
     * 래핑한 {@link ServerSetting}을 저장한 뒤 반환한다.</p>
     *
     * <p>유스케이스: {@link com.example.serverprovision.application.setting.controller.SettingController#createSetting}에서
     * 호출된다. Resolver들이 내부에서 수행하는 Repository 조회들이 동일 트랜잭션에 참여하도록
     * {@code @Transactional}이 적용된다. 반환된 엔티티는 컨트롤러에서
     * {@link com.example.serverprovision.application.setting.dto.SettingCreateResponse#from}으로
     * 매핑되어 201 Created 응답 본문으로 사용된다.
     * 저장된 {@link ServerSetting}의 초기 {@code status}는 {@link com.example.serverprovision.application.setting.model.enums.SettingStatus#PENDING}이다.</p>
     *
     * <p>확장 가이드: 저장 이후 알림·이벤트 발행이 필요하다면 이 메소드 말미에
     * {@code ApplicationEventPublisher}로 이벤트를 발행한다. 새 프로세스 타입 추가는
     * 이 메소드를 수정할 필요 없이 {@link SettingProcessResolver} 구현체를 추가하면 된다.</p>
     *
     * @param request 세팅 주문서 생성 요청 DTO
     * @return 저장된 {@link ServerSetting} 엔티티 (id가 채워진 상태)
     * @throws com.example.serverprovision.global.exception.FieldValidationException
     *         지원하지 않는 프로세스 타입이거나 Resolver 내부 검증 실패 시
     */
    @Transactional
    public ServerSetting save(SettingCreateRequest request) {
        log.info("[SettingService] 세팅 주문서 생성 시작. name={}, 단계 수={}",
                request.name(), request.processList().size());

        // 요청 DTO → 도메인 모델 해석 (polymorphic dispatch via SettingProcessResolver).
        // 각 resolver 내부에서 발생한 FieldValidationException 은 요청 DTO 기준 로컬
        // 필드 이름만 가지고 있으므로, 여기서 processList[i]. 인덱스 프리픽스를 붙여
        // Bean Validation 이 생성하는 경로 포맷(processList[0].partitions)과 일치시킨다.
        // .sorted() 는 AbstractSettingProcess#compareTo 가 processStep.getOrder() 기준이므로
        // 도메인 불변식(단계 실행 순서 정렬)을 서비스 레이어에서 방어적으로 보장한다.
        List<AbstractProcessRequest> requests = request.processList();
        List<AbstractSettingProcess> resolvedProcesses = IntStream.range(0, requests.size())
                .mapToObj(i -> resolveOneAtIndex(requests.get(i), i))
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

        ServerSetting saved = settingRepository.save(setting);
        log.info("[SettingService] ServerSetting 저장 완료. id={}, name={}, status={}",
                saved.getId(), saved.getName(), saved.getStatus());
        return saved;
    }

    /**
     * 단일 {@link AbstractProcessRequest}를 담당 Resolver에 dispatch하고,
     * 내부에서 발생한 {@link FieldValidationException}에 {@code processList[index].} 프리픽스를 부착한다.
     *
     * <p>역할: {@link #save}의 stream 처리 내부에서 각 요청 항목을 개별적으로 처리하는
     * 헬퍼 메소드이다. {@link FieldValidationException}의 필드 경로에 인덱스 프리픽스를 붙여
     * Bean Validation 경로 포맷({@code processList[0].partitions})과 일치시킨다.</p>
     *
     * <p>유스케이스: {@link #save}에서 {@code IntStream.range}로 인덱스와 함께 호출된다.
     * 지원하는 Resolver가 없으면 {@code processList[index]} 수준의 섹션 에러로 승급하고,
     * {@link FieldValidationException} 외의 예외(예: {@link IllegalArgumentException})는
     * 그대로 전파되어 {@link com.example.serverprovision.global.exception.GlobalExceptionHandler}의
     * 폼 레벨 핸들러가 처리한다.</p>
     *
     * <p>확장 가이드: 이 메소드 자체는 수정할 필요가 없다. 새 프로세스 타입 추가 시
     * {@link SettingProcessResolver}를 구현하는 새 {@code @Component}를 등록하면
     * {@code supports} 판별에 자동으로 포함된다.</p>
     *
     * @param req   처리할 단일 요청 DTO
     * @param index {@code processList} 내 0-기반 인덱스 (에러 경로 프리픽스 생성용)
     * @return 해석된 {@link AbstractSettingProcess} 도메인 모델
     * @throws FieldValidationException 지원하는 Resolver가 없거나 Resolver 내부 검증 실패 시
     */
    private AbstractSettingProcess resolveOneAtIndex(AbstractProcessRequest req, int index) {
        log.info("[SettingService] 프로세스 요청 처리 중. index={}, type={}", index, req.getClass().getSimpleName());
        SettingProcessResolver resolver = resolvers.stream()
                .filter(r -> r.supports(req))
                .findFirst()
                .orElseThrow(() -> new FieldValidationException(
                        "processList[" + index + "]",
                        "지원하지 않는 프로세스 요청 타입입니다: " + req.getClass().getSimpleName()));
        try {
            return resolver.resolve(req);
        } catch (FieldValidationException ex) {
            throw new FieldValidationException(
                    "processList[" + index + "]." + ex.getField(),
                    ex.getMessage(),
                    ex);
        }
    }
}

package com.example.serverprovision.application.setting.service;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.dto.SettingCreateRequest;
import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.OSInstallation;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.application.setting.model.enums.SettingStatus;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.application.setting.model.request.RootPasswordRequest;
import com.example.serverprovision.application.setting.model.request.UserRequest;
import com.example.serverprovision.application.setting.repository.SettingRepository;
import com.example.serverprovision.application.setting.service.resolver.SettingProcessResolver;
import com.example.serverprovision.domain.os.model.installation.LinuxInstallation;
import com.example.serverprovision.global.exception.FieldValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
     * ID로 세팅 주문서 단건을 조회한다.
     *
     * <p>역할: {@link SettingRepository#findById}로 특정 {@link ServerSetting}을 조회하여 반환한다.
     * 읽기 전용 트랜잭션으로 실행된다.</p>
     *
     * <p>유스케이스: {@link com.example.serverprovision.application.setting.controller.SettingController}의
     * {@code GET /pxe/v1/setting/{id}} 핸들러가 호출하여 세팅 주문서 상세 페이지에 데이터를 제공한다.</p>
     *
     * @param id 조회할 세팅 주문서의 PK
     * @return 조회된 {@link ServerSetting}를 감싼 {@link Optional}. 없으면 empty.
     */
    @Transactional(readOnly = true)
    public Optional<ServerSetting> findById(Long id) {
        return settingRepository.findById(id);
    }

    /**
     * 저장된 모든 세팅 주문서를 생성일시 내림차순으로 반환한다.
     *
     * <p>역할: {@link SettingRepository#findAll(Sort)}로 전체 {@link ServerSetting} 목록을
     * 조회하여 반환한다. 읽기 전용 트랜잭션으로 실행되어 불필요한 변경 감지를 방지한다.</p>
     *
     * <p>유스케이스: {@link com.example.serverprovision.application.setting.controller.SettingController}의
     * {@code GET /pxe/v1/setting} 핸들러가 호출하여 세팅 주문서 목록 페이지에 데이터를 제공한다.</p>
     *
     * @return 생성일시 내림차순으로 정렬된 {@link ServerSetting} 전체 목록
     */
    @Transactional(readOnly = true)
    public List<ServerSetting> findAll() {
        return settingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

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

        List<AbstractSettingProcess> resolvedProcesses = resolveProcessList(request.processList());

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
     * 세팅 주문서를 수정한다.
     *
     * <p>역할: ID로 기존 {@link ServerSetting}을 조회하고 PENDING 상태를 확인한 뒤,
     * {@link SettingCreateRequest}를 resolve하여 도메인 모델로 변환하고
     * {@link ServerSetting#update}를 호출한다. JPA Dirty Checking으로 자동 UPDATE 된다.</p>
     *
     * <p>유스케이스: {@link com.example.serverprovision.application.setting.controller.SettingController#updateSetting}에서
     * 호출된다. PENDING이 아닌 상태의 주문서 수정 시도 시 {@link IllegalStateException}을 던지며,
     * {@link com.example.serverprovision.global.exception.GlobalExceptionHandler}가 409 Conflict로 처리한다.</p>
     *
     * @param id      수정할 세팅 주문서의 PK
     * @param request 수정 요청 DTO ({@link SettingCreateRequest}와 동일한 스키마)
     * @return 수정된 {@link ServerSetting} 엔티티
     * @throws IllegalArgumentException 존재하지 않는 ID인 경우
     * @throws IllegalStateException    PENDING 상태가 아닌 주문서를 수정 시도할 경우
     */
    @Transactional
    public ServerSetting update(Long id, SettingCreateRequest request) {
        // 비관적 쓰기 락 획득: PENDING 상태 확인과 UPDATE SQL 발행 사이에
        // 다른 트랜잭션이 status 를 변경하는 race condition 을 차단한다.
        ServerSetting setting = settingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 세팅 주문서입니다. id=" + id));

        if (setting.getStatus() != SettingStatus.PENDING) {
            throw new IllegalStateException(
                    setting.getStatus().getDescription()
                    + " 상태의 주문서는 수정할 수 없습니다. PENDING 상태일 때만 가능합니다.");
        }

        log.info("[SettingService] 세팅 주문서 수정 시작. id={}, name={}, 단계 수={}",
                id, request.name(), request.processList().size());

        // keepExistingPassword 플래그가 설정된 항목에 기존 비밀번호를 주입한다.
        // Resolver와 도메인 모델은 항상 유효한 비밀번호를 받을 수 있게 된다.
        List<AbstractProcessRequest> patchedProcessList =
                patchKeepExistingPasswords(request.processList(), setting);
        List<AbstractSettingProcess> resolved = resolveProcessList(patchedProcessList);
        setting.update(request.name(), new SettingProcess(resolved));

        log.info("[SettingService] 세팅 주문서 수정 완료. id={}, name={}", id, request.name());
        return setting;
    }

    /**
     * 요청 DTO 목록을 resolver dispatch하여 정렬된 도메인 모델 목록으로 변환한다.
     *
     * <p>역할: {@link #save}와 {@link #update} 양쪽에서 공유하는 핵심 변환 로직이다.
     * 각 요청 항목을 담당 {@link SettingProcessResolver}에 dispatch하고, 결과를
     * {@link AbstractSettingProcess#compareTo} 기준으로 정렬한다.</p>
     *
     * @param requests 변환할 요청 DTO 목록
     * @return 정렬된 도메인 모델 목록
     */
    private List<AbstractSettingProcess> resolveProcessList(List<AbstractProcessRequest> requests) {
        // 요청 DTO → 도메인 모델 해석 (polymorphic dispatch via SettingProcessResolver).
        // 각 resolver 내부에서 발생한 FieldValidationException 은 요청 DTO 기준 로컬
        // 필드 이름만 가지고 있으므로, 여기서 processList[i]. 인덱스 프리픽스를 붙여
        // Bean Validation 이 생성하는 경로 포맷(processList[0].partitions)과 일치시킨다.
        // .sorted() 는 AbstractSettingProcess#compareTo 가 processStep.getOrder() 기준이므로
        // 도메인 불변식(단계 실행 순서 정렬)을 서비스 레이어에서 방어적으로 보장한다.
        return IntStream.range(0, requests.size())
                .mapToObj(i -> resolveOneAtIndex(requests.get(i), i))
                .sorted()
                .toList();
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

    /**
     * 수정 요청 DTO 목록에서 {@code keepExistingPassword} 플래그가 설정된 비밀번호 필드를
     * 기존 저장값으로 교체한 새 목록을 반환한다.
     *
     * <p>역할: 수정 폼에서 비밀번호를 비워 제출하면 프론트엔드가 {@code keepExistingPassword: true}를
     * 전송한다. 이 메서드는 플래그를 감지하여 기존 {@link ServerSetting}에서 비밀번호를 추출한 뒤
     * 새 DTO 객체에 주입함으로써, Resolver와 도메인 모델이 항상 유효한 비밀번호를 받도록 한다.</p>
     *
     * @param processList     수정 요청의 프로세스 목록
     * @param existingSetting 기존 저장된 세팅 주문서 (비밀번호 원본 출처)
     * @return 비밀번호가 패치된 새로운 프로세스 목록
     */
    private List<AbstractProcessRequest> patchKeepExistingPasswords(
            List<AbstractProcessRequest> processList, ServerSetting existingSetting) {

        // 기존 세팅의 프로세스를 단계 유형 기준으로 빠르게 조회할 수 있도록 맵을 구성한다.
        Map<SettingProcessStep, AbstractSettingProcess> existingByStep =
                existingSetting.getSettingProcess().processList().stream()
                        .collect(Collectors.toMap(AbstractSettingProcess::getProcessStep, p -> p));

        return processList.stream().map(req -> {
            if (!(req instanceof OSInstallationRequest osReq)) {
                return req;
            }

            AbstractSettingProcess existing = existingByStep.get(SettingProcessStep.OS_INSTALLATION);
            if (existing == null) {
                return req;
            }

            // 기존 도메인 모델에서 비밀번호 원본을 추출한다.
            // 현재 구현체는 모두 LinuxInstallation 하위 타입이므로 캐스팅이 안전하다.
            if (!(((OSInstallation) existing).getOsInstallation() instanceof LinuxInstallation existingLinux)) {
                return req;
            }

            RootPasswordRequest patchedRoot =
                    patchRootPassword(osReq.getRootPassword(), existingLinux.getRootPassword());
            List<UserRequest> patchedUsers =
                    patchUsers(osReq.getUsers(), existingLinux.getUsers());

            // OS 패밀리별로 보유 필드가 다르므로 구체 타입에 위임 — RHEL 은 environmentId/KDump 등,
            // Ubuntu 는 hostname/packages 등이 보존되어야 한다.
            return osReq.withPatchedPasswords(patchedRoot, patchedUsers);
        }).toList();
    }

    /**
     * root 비밀번호 요청 DTO에서 {@code keepExistingPassword} 플래그를 처리한다.
     *
     * @param incoming 수정 요청의 root 비밀번호 DTO ({@code null}이면 root 계정 잠금 의도)
     * @param existing 기존 저장된 도메인 root 비밀번호 ({@code null}이면 기존에 잠금 상태였음)
     * @return 비밀번호가 주입된 새 DTO, 또는 변경 없이 원본 반환
     */
    private RootPasswordRequest patchRootPassword(
            RootPasswordRequest incoming,
            com.example.serverprovision.domain.os.model.installation.RootPassword existing) {
        if (incoming == null || !incoming.isKeepExistingPassword()) {
            return incoming;
        }
        if (existing == null) {
            // 기존에 root 비밀번호가 없었으면 잠금 상태(null)로 유지한다.
            return null;
        }
        return new RootPasswordRequest(
                existing.getPassword(),
                existing.isPasswordEncrypted(),
                false
        );
    }

    /**
     * 일반 사용자 요청 DTO 목록에서 {@code keepExistingPassword} 플래그를 처리한다.
     *
     * @param incoming 수정 요청의 사용자 DTO 목록
     * @param existing 기존 저장된 도메인 사용자 목록
     * @return 비밀번호가 주입된 새 DTO 목록
     */
    private List<UserRequest> patchUsers(
            List<UserRequest> incoming,
            List<com.example.serverprovision.domain.os.model.installation.User> existing) {
        if (incoming == null || incoming.isEmpty()) {
            return incoming;
        }
        // 기존 사용자를 이름 기준으로 빠르게 조회할 수 있도록 맵을 구성한다.
        Map<String, com.example.serverprovision.domain.os.model.installation.User> existingByUsername =
                existing.stream().collect(Collectors.toMap(
                        com.example.serverprovision.domain.os.model.installation.User::getUsername,
                        u -> u));
        return incoming.stream().map(u -> {
            if (!u.isKeepExistingPassword()) {
                return u;
            }
            var existingUser = existingByUsername.get(u.getUsername());
            if (existingUser == null) {
                // 기존에 없던 사용자명이면 그대로 전달하고 도메인 검증에서 처리된다.
                return u;
            }
            return new UserRequest(
                    u.getUsername(),
                    existingUser.getPassword(),
                    u.getIsSudoer(),
                    u.isPasswordEncrypted(),
                    false
            );
        }).toList();
    }
}

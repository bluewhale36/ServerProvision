package com.example.serverprovision.domain.node.entity;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.model.BasicSetting;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.domain.node.model.enums.JobType;
import com.example.serverprovision.domain.node.model.enums.ProvisioningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ServerNode} 엔티티 도메인 메서드 단위 테스트.
 *
 * <p>순수 Java 객체 테스트이며 Spring 컨텍스트를 사용하지 않는다.</p>
 */
class ServerNodeTest {

    // =========================================================================
    // assignSetting
    // =========================================================================

    @Nested
    @DisplayName("assignSetting: 세팅 할당 후 필드 갱신 검증")
    class AssignSettingTests {

        @Test
        @DisplayName("assignSetting 호출 시 serverSetting 과 targetJob 이 갱신된다")
        void assignSetting_updatesBothFields() {
            // given
            ServerNode node = ServerNode.builder()
                    .id(1L)
                    .macAddress("AA:BB:CC:DD:EE:FF")
                    .targetJob(JobType.IDLE)
                    .status(ProvisioningStatus.NEW)
                    .build();

            ServerSetting setting = ServerSetting.builder()
                    .id(10L)
                    .name("테스트 세팅")
                    .settingProcess(new SettingProcess(List.of(new BasicSetting())))
                    .build();

            // when
            node.assignSetting(setting, JobType.OS_INSTALLATION);

            // then
            assertThat(node.getServerSetting()).isEqualTo(setting);
            assertThat(node.getTargetJob()).isEqualTo(JobType.OS_INSTALLATION);
        }

        @Test
        @DisplayName("이미 세팅이 있는 노드에 새 세팅을 할당하면 기존 값을 덮어쓴다")
        void assignSetting_overwritesExisting() {
            // given
            ServerSetting oldSetting = ServerSetting.builder()
                    .id(10L)
                    .name("이전 세팅")
                    .build();

            ServerNode node = ServerNode.builder()
                    .id(1L)
                    .macAddress("AA:BB:CC:DD:EE:FF")
                    .targetJob(JobType.BIOS_UPDATE)
                    .status(ProvisioningStatus.IN_PROGRESS)
                    .serverSetting(oldSetting)
                    .build();

            ServerSetting newSetting = ServerSetting.builder()
                    .id(20L)
                    .name("새 세팅")
                    .build();

            // when
            node.assignSetting(newSetting, JobType.OS_INSTALLATION);

            // then: 새 세팅으로 교체
            assertThat(node.getServerSetting()).isEqualTo(newSetting);
            assertThat(node.getServerSetting().getId()).isEqualTo(20L);
            assertThat(node.getTargetJob()).isEqualTo(JobType.OS_INSTALLATION);
        }

        @Test
        @DisplayName("null 세팅을 할당하면 serverSetting 이 null 이 된다")
        void assignSetting_withNull_setsNull() {
            // given
            ServerNode node = ServerNode.builder()
                    .id(1L)
                    .macAddress("AA:BB:CC:DD:EE:FF")
                    .targetJob(JobType.OS_INSTALLATION)
                    .status(ProvisioningStatus.IN_PROGRESS)
                    .serverSetting(ServerSetting.builder().id(10L).build())
                    .build();

            // when
            node.assignSetting(null, JobType.IDLE);

            // then
            assertThat(node.getServerSetting()).isNull();
            assertThat(node.getTargetJob()).isEqualTo(JobType.IDLE);
        }
    }

    // =========================================================================
    // startProvisioning
    // =========================================================================

    @Nested
    @DisplayName("startProvisioning: 상태 전이 검증")
    class StartProvisioningTests {

        @Test
        @DisplayName("startProvisioning 호출 시 status 가 IN_PROGRESS 로 변경된다")
        void startProvisioning_changesStatusToInProgress() {
            // given
            ServerNode node = ServerNode.builder()
                    .id(1L)
                    .macAddress("AA:BB:CC:DD:EE:FF")
                    .status(ProvisioningStatus.NEW)
                    .build();

            // when
            node.startProvisioning();

            // then
            assertThat(node.getStatus()).isEqualTo(ProvisioningStatus.IN_PROGRESS);
        }
    }

    // =========================================================================
    // create 팩토리 메서드
    // =========================================================================

    @Nested
    @DisplayName("create(mac, boardModel): 팩토리 메서드 검증")
    class CreateFactoryTests {

        @Test
        @DisplayName("MAC 주소와 BoardModel 로 생성하면 IDLE/NEW 초기 상태이다")
        void create_setsInitialState() {
            // given
            var boardModel = com.example.serverprovision.domain.board.entity.BoardModel.builder()
                    .id(1L)
                    .build();

            // when
            ServerNode node = ServerNode.create("11:22:33:44:55:66", boardModel);

            // then
            assertThat(node.getMacAddress()).isEqualTo("11:22:33:44:55:66");
            assertThat(node.getBoardModel()).isEqualTo(boardModel);
            assertThat(node.getTargetJob()).isEqualTo(JobType.IDLE);
            assertThat(node.getStatus()).isEqualTo(ProvisioningStatus.NEW);
            assertThat(node.getServerSetting()).isNull();
        }
    }
}

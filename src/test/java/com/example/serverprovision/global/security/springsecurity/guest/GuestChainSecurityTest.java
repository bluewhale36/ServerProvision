package com.example.serverprovision.global.security.springsecurity.guest;

import com.example.serverprovision.global.security.springsecurity.guest.authentication.GuestPrincipalResolver;
import com.example.serverprovision.global.security.springsecurity.guest.web.GuestAccessDeniedHandler;
import com.example.serverprovision.global.security.springsecurity.guest.web.GuestAuthenticationEntryPoint;

import com.example.serverprovision.execution.controller.ExecutionRestController;
import com.example.serverprovision.execution.controller.GuestAgentRestController;
import com.example.serverprovision.execution.controller.WindowsInstallAssetRestController;
import com.example.serverprovision.execution.dto.response.AgentCheckinResponse;
import com.example.serverprovision.execution.engine.diagnose.AgentReportService;
import com.example.serverprovision.execution.engine.windows.WindowsInstallTokenRegistry;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.engine.boot.BootService;
import com.example.serverprovision.execution.service.FirmwareImageTokenRegistry;
import com.example.serverprovision.execution.vo.GuestToken;
import com.example.serverprovision.global.security.springsecurity.authorization.MustChangePasswordSuccessHandler;
import com.example.serverprovision.global.security.springsecurity.authorization.SignupAccessPolicy;
import com.example.serverprovision.global.security.springsecurity.config.GuestSecurityConfig;
import com.example.serverprovision.global.security.springsecurity.config.PasswordEncoderConfig;
import com.example.serverprovision.global.security.springsecurity.config.SecurityConfig;
import com.example.serverprovision.global.security.springsecurity.controller.LoginController;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S19-1 — 게스트 체인의 경계를 실제 두 SecurityFilterChain(게스트 · 웹)으로 본다: credential 없음 · 위조 · 회수 = 401 JSON, 정상 = 컨트롤러 도달,
 * /boot 는 익명 통과(틀린 credential 은 401), 웹 사용자 principal 은 게스트 권한이 없다(403), 웹 체인은 그대로(302 /login).
 * <p>S19-2 — 첫 접촉(/boot · /assets)은 PXE 부팅 계정의 Basic 이 연다(401 text + WWW-Authenticate · 틀린 secret 401 · Basic 만으로 /agent 는 403),
 * 출발지 대역 밖은 credential 과 무관하게 403 PXE_LAN_FORBIDDEN.</p>
 */
@WebMvcTest(controllers = {GuestAgentRestController.class, WindowsInstallAssetRestController.class, ExecutionRestController.class, LoginController.class})
@Import({GuestSecurityConfig.class, SecurityConfig.class, PasswordEncoderConfig.class, SignupAccessPolicy.class, MustChangePasswordSuccessHandler.class,
		GuestPrincipalResolver.class, GuestAuthenticationEntryPoint.class, GuestAccessDeniedHandler.class,
		// S19-2 — 첫 접촉 credential · LAN 정책 · URL 조립
		com.example.serverprovision.execution.config.PxeBootProperties.class, com.example.serverprovision.execution.engine.boot.PxeBootUrls.class,
		com.example.serverprovision.global.security.springsecurity.guest.authorization.ProvisioningLanPolicy.class,
		com.example.serverprovision.global.security.springsecurity.guest.web.PxeBootEntryPoint.class})
@TestPropertySource(properties = {"pxe.boot.secret=s3cret", "pxe.guest.allowed-cidrs=127.0.0.1/32,10.0.2.0/24"})   // MockMvc 출발지 = 127.0.0.1
class GuestChainSecurityTest {

	private static final UUID GUEST_ID = UUID.randomUUID();
	private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";

	@Autowired WebApplicationContext context;
	@MockitoBean AgentReportService agentReportService;
	@MockitoBean BootService bootService;
	@MockitoBean GuestServerRepository guestServerRepository;
	@MockitoBean WindowsInstallTokenRegistry windowsInstallTokenRegistry;
	@MockitoBean FirmwareImageTokenRegistry firmwareImageTokenRegistry;
	@MockitoBean UsersService usersService;
	@MockitoBean UserDetailsService userDetailsService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		given(guestServerRepository.findByGuestToken(new GuestToken(TOKEN)))
				.willReturn(Optional.of(GuestServer.builder().id(GUEST_ID).systemUUID(UUID.randomUUID()).build()));
		given(guestServerRepository.findByGuestToken(new GuestToken("bad"))).willReturn(Optional.empty());
	}

	@Test
	@DisplayName("에이전트 채널 — 헤더 없음 · 위조는 401 JSON(GUEST_UNAUTHENTICATED) · 서비스 미호출, 정상 토큰은 컨트롤러 도달")
	void agentChannel_headerToken() throws Exception {
		mvc.perform(post("/api/pxe/v1/agent/checkin"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("GUEST_UNAUTHENTICATED"))
				.andExpect(jsonPath("$.message").value("게스트 인증이 필요합니다."));
		mvc.perform(post("/api/pxe/v1/agent/checkin").header("X-Guest-Token", "bad"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("GUEST_UNAUTHENTICATED"));
		verify(agentReportService, never()).checkin(any());

		given(agentReportService.checkin(any())).willReturn(new AgentCheckinResponse(com.example.serverprovision.execution.enums.AgentDirective.COLLECT, "guest-01"));
		mvc.perform(post("/api/pxe/v1/agent/checkin").header("X-Guest-Token", TOKEN))
				.andExpect(status().isOk());
		verify(agentReportService).checkin(new GuestPrincipal(GUEST_ID, guestServerRepository.findByGuestToken(new GuestToken(TOKEN)).get().getSystemUUID()));
	}

	@Test
	@DisplayName("서빙 경로 — 회수 · 미발급 · 형식 위반 토큰은 401, 레지스트리가 아는 토큰은 체인을 지나 컨트롤러가 푼다(헤더는 읽지 않는다)")
	void servingPath_pathToken() throws Exception {
		UUID unknown = UUID.randomUUID();
		given(windowsInstallTokenRegistry.guestOf(unknown)).willReturn(Optional.empty());
		mvc.perform(get("/api/pxe/v1/windows/" + unknown + "/wimboot")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/pxe/v1/windows/not-a-uuid/wimboot")).andExpect(status().isUnauthorized());

		UUID issued = UUID.randomUUID();
		given(windowsInstallTokenRegistry.guestOf(issued)).willReturn(Optional.of(GUEST_ID));
		given(guestServerRepository.findById(GUEST_ID))
				.willReturn(Optional.of(GuestServer.builder().id(GUEST_ID).systemUUID(UUID.randomUUID()).build()));
		given(windowsInstallTokenRegistry.resolve(issued)).willReturn(Optional.empty());   // 체인 통과 뒤 컨트롤러의 해석(404) — 401 이 아님이 요점
		mvc.perform(get("/api/pxe/v1/windows/" + issued + "/wimboot").header("X-Guest-Token", "bad"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("/boot — PXE 부팅 계정 Basic 으로 통과 · 틀린 게스트 토큰 헤더를 들고 오면 Basic 이 맞아도 401 JSON(credential 이 있으면 대조한다)")
	void boot_withBasic_butWrongTokenRejected() throws Exception {
		given(bootService.boot(any(), anyString())).willReturn("#!ipxe\nexit");
		given(bootService.boot(any(), any())).willReturn("#!ipxe\nexit");
		mvc.perform(get("/api/pxe/v1/boot").with(httpBasic("pxe", "s3cret")).param("systemUUID", UUID.randomUUID().toString()).param("macAddress", "00:11:22:33:44:55")
						.param("ipAddress", "192.168.1.150").param("vendor", "Giga Computing").param("boardModel", "MS03-CE0"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/pxe/v1/boot").with(httpBasic("pxe", "s3cret")).header("X-Guest-Token", "bad").param("systemUUID", UUID.randomUUID().toString()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("GUEST_UNAUTHENTICATED"));
	}

	@Test
	@DisplayName("S19-2 첫 접촉 — credential 없음 · 틀린 secret · 틀린 사용자명은 401 text + WWW-Authenticate Basic · 서비스 미호출, /assets 도 같은 entry point")
	void firstContact_requiresPxeBootBasic() throws Exception {
		mvc.perform(get("/api/pxe/v1/boot").param("systemUUID", UUID.randomUUID().toString()))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Basic realm=\"provision-pxe\""))
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("PXE 부팅 credential")));
		mvc.perform(get("/api/pxe/v1/boot").with(httpBasic("pxe", "wrong")).param("systemUUID", UUID.randomUUID().toString()))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Basic realm=\"provision-pxe\""))
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
		mvc.perform(get("/api/pxe/v1/boot").with(httpBasic("admin", "s3cret")).param("systemUUID", UUID.randomUUID().toString()))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/pxe/v1/assets/vmlinuz-lts"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().exists("WWW-Authenticate"));
		verify(bootService, never()).boot(any(), any());
		// credential 이 맞으면 체인을 지난다 — 자산 컨트롤러는 이 슬라이스에 없으므로 404 가 "통과" 의 증거
		mvc.perform(get("/api/pxe/v1/assets/vmlinuz-lts").with(httpBasic("pxe", "s3cret")))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("S19-2 F-1 — /assets/agent.sh 만 credential 없이 연다(firstboot 가 provision_base 로 받는다) · 대역 제한은 유지 · 다른 자산은 여전히 401")
	void agentScript_lanOnlyWithoutCredential() throws Exception {
		// 자산 컨트롤러는 이 슬라이스에 없으므로 404 가 "체인 통과" 의 증거(401 · 403 이 아니다)
		mvc.perform(get("/api/pxe/v1/assets/agent.sh")).andExpect(status().isNotFound());
		// 익명 + 대역 밖은 403 이 아니라 401 — 익명의 인가 거절은 ExceptionTranslationFilter 가 entry point 로 보낸다(O-6 · plan 개정)
		mvc.perform(get("/api/pxe/v1/assets/agent.sh").with(r -> { r.setRemoteAddr("172.16.0.5"); return r; }))
				.andExpect(status().isUnauthorized())
				.andExpect(header().exists("WWW-Authenticate"));
		mvc.perform(get("/api/pxe/v1/assets/diag.apkovl.tar.gz")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("S19-2 — Basic credential 만으로 에이전트 채널은 403 JSON(GUEST_FORBIDDEN) · entry point 은 JSON(WWW-Authenticate 없음)")
	void basicOnly_isNotGuest() throws Exception {
		mvc.perform(post("/api/pxe/v1/agent/checkin").with(httpBasic("pxe", "s3cret")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GUEST_FORBIDDEN"));
		mvc.perform(post("/api/pxe/v1/agent/checkin"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("WWW-Authenticate"))
				.andExpect(jsonPath("$.code").value("GUEST_UNAUTHENTICATED"));
		verify(agentReportService, never()).checkin(any());
	}

	@Test
	@DisplayName("S19-2 LAN — 출발지가 허용 CIDR 밖이면 credential 이 맞아도 403 JSON(PXE_LAN_FORBIDDEN) · 첫 접촉 · 에이전트 채널 둘 다")
	void outsideLan_isForbidden() throws Exception {
		mvc.perform(get("/api/pxe/v1/boot").with(httpBasic("pxe", "s3cret")).with(r -> { r.setRemoteAddr("172.16.0.5"); return r; })
						.param("systemUUID", UUID.randomUUID().toString()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PXE_LAN_FORBIDDEN"));
		mvc.perform(post("/api/pxe/v1/agent/checkin").header("X-Guest-Token", TOKEN).with(r -> { r.setRemoteAddr("172.16.0.5"); return r; }))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PXE_LAN_FORBIDDEN"));
		verify(bootService, never()).boot(any(), any());
		verify(agentReportService, never()).checkin(any());
		// 허용 대역(10.0.2.0/24 — QEMU 랩)은 통과
		mvc.perform(get("/api/pxe/v1/boot").with(httpBasic("pxe", "s3cret")).with(r -> { r.setRemoteAddr("10.0.2.15"); return r; })
						.param("systemUUID", UUID.randomUUID().toString()))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = {"ADMIN", "USER"})
	@DisplayName("웹 사용자 principal 은 게스트 권한이 아니다 — /agent/checkin 은 403 JSON(GUEST_FORBIDDEN)")
	void webUser_isNotGuest() throws Exception {
		mvc.perform(post("/api/pxe/v1/agent/checkin"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GUEST_FORBIDDEN"));
		verify(agentReportService, never()).checkin(any());
	}

	@Test
	@DisplayName("웹 formLogin 무회귀 — 게스트 provider 가 있어도 아이디 · 비밀번호 로그인이 산다(CP5 F-1: AuthenticationProvider 빈이 전역 매니저를 독점하던 것)")
	void webFormLogin_stillWorks() throws Exception {
		org.springframework.security.crypto.password.PasswordEncoder encoder = context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
		com.example.serverprovision.global.security.springsecurity.entity.Users admin = com.example.serverprovision.global.security.springsecurity.entity.Users.builder()
				.id(1L).username("admin").password(encoder.encode("Passw0rd!")).name("관리자").isEnabled(true).isLocked(false).mustChangePassword(false).build();
		given(userDetailsService.loadUserByUsername("admin")).willReturn(new com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl(
				admin, java.util.List.of(com.example.serverprovision.global.security.springsecurity.domain.Role.ADMIN, com.example.serverprovision.global.security.springsecurity.domain.Role.USER)));
		mvc.perform(post("/login").param("username", "admin").param("password", "Passw0rd!"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/"));
	}

	@Test
	@DisplayName("웹 체인 무회귀 — /management/os 익명은 302 /login · 게스트 토큰 헤더는 웹 체인에서 아무 뜻이 없다")
	void webChain_untouched() throws Exception {
		mvc.perform(get("/management/os").accept(MediaType.TEXT_HTML).header("X-Guest-Token", TOKEN))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}
}

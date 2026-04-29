package com.example.serverprovision.global.marker.service;

import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.exception.MarkerWriteFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * `.provision.json` 마커 파일의 도메인 무관 인프라 (MK1 에서 BIOS 측 ProvisionMarkerService 를 일반화 이전).
 *
 * <p>두 layout 지원:
 * <ul>
 *   <li>{@link MarkerLayout#IN_TREE} — 디렉토리 자원의 트리 루트 안 {@code .provision.json}</li>
 *   <li>{@link MarkerLayout#SIDECAR} — 단일 파일 자원 형제 {@code <basename>.provision.json}</li>
 * </ul>
 *
 * <p>HMAC 비밀 키는 환경변수 {@code PROVISION_MARKER_SECRET} 으로 주입. 미설정 시 dev 용 default
 * (운영 환경에선 반드시 override). secret 회전 시 모든 마커 재발급 마이그레이션 필요 (별도 admin 도구로 후속).</p>
 */
@Slf4j
@Service
public class ProvisionMarkerService {

    public static final String MARKER_FILENAME = ".provision.json";
    public static final String SIDECAR_SUFFIX = ".provision.json";

    private static final String HMAC_ALGO = "HmacSHA256";

    /** 운영 환경 secret 미설정 시 내부에 남는 dev 기본값. 부팅 시 fail-fast 가드의 비교 기준. */
    static final String DEFAULT_DEV_SECRET = "change-me-before-prod-deploy";

    /**
     * 마커 직렬화는 결정론이 필수다 — 같은 입력은 모든 JVM 에서 동일한 canonical JSON 을 생산해야
     * HMAC 서명이 재계산 시점에도 일치한다. {@code Map.of(...)} 의 이터레이션 순서는 JVM 인스턴스마다
     * 무작위라 키 정렬을 강제하지 않으면 서버 재시작 후 같은 데이터의 서명이 어긋나 SIGNATURE_INVALID
     * 오탐이 발생한다. SORT_PROPERTIES_ALPHABETICALLY + ORDER_MAP_ENTRIES_BY_KEYS 두 플래그로 고정.
     */
    private final ObjectMapper om = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Value("${provision.marker.secret:" + DEFAULT_DEV_SECRET + "}")
    private String secret;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    /**
     * 운영 환경에서 dev 기본 secret 으로 부팅하지 않도록 fail-fast 가드.
     * dev/test 프로필에선 경고 로그만, 그 외 (prod 추정) 에선 IllegalStateException 으로 중단.
     */
    @PostConstruct
    void verifySecretConfiguration() {
        if (!DEFAULT_DEV_SECRET.equals(secret)) {
            return;
        }
        boolean devLike = activeProfiles == null || activeProfiles.isBlank()
                || activeProfiles.contains("dev") || activeProfiles.contains("test")
                || activeProfiles.contains("local");
        if (devLike) {
            log.warn("[provision.marker] dev 기본 secret 사용 중 — 운영 배포 전 PROVISION_MARKER_SECRET 환경변수로 반드시 override 하십시오.");
        } else {
            throw new IllegalStateException(
                    "provision.marker.secret 가 dev 기본값입니다. 운영 환경에서는 PROVISION_MARKER_SECRET 환경변수로 override 가 필수입니다.");
        }
    }

    /** 마커 파일의 실제 디스크 경로 계산. IN_TREE: {@code <dir>/.provision.json}, SIDECAR: {@code <file>.provision.json}. */
    public Path resolveMarkerFile(Path resourcePath, MarkerLayout layout) {
        return switch (layout) {
            case IN_TREE -> resourcePath.resolve(MARKER_FILENAME);
            case SIDECAR -> resourcePath.resolveSibling(resourcePath.getFileName().toString() + SIDECAR_SUFFIX);
        };
    }

    /**
     * 마커를 자원 위치에 기록. 서명은 caller 가 계산한 값을 {@code content.signature} 에 담아 전달.
     * IN_TREE 의 경우 디렉토리 부재 시 자동 생성. SIDECAR 는 부모 디렉토리가 이미 있어야 한다.
     */
    public void write(Path resourcePath, MarkerLayout layout, MarkerContent content) {
        Path target = resolveMarkerFile(resourcePath, layout);
        try {
            byte[] bytes = om.writeValueAsBytes(content);
            if (layout == MarkerLayout.IN_TREE) {
                Files.createDirectories(resourcePath);
            }
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new MarkerWriteFailedException("marker 파일 기록 실패 : " + target, e);
        }
    }

    /**
     * 마커 파일을 읽어 역직렬화.
     * @throws MarkerMissingException 파일이 존재하지 않음
     * @throws MarkerWriteFailedException JSON 파싱 실패
     */
    public MarkerContent read(Path resourcePath, MarkerLayout layout) {
        Path target = resolveMarkerFile(resourcePath, layout);
        if (!Files.exists(target)) {
            throw new MarkerMissingException(target.toString());
        }
        try {
            return om.readValue(Files.readAllBytes(target), MarkerContent.class);
        } catch (IOException e) {
            throw new MarkerWriteFailedException("marker 파일 파싱 실패 : " + target, e);
        }
    }

    /** 주어진 content 의 payload (signature 제외) 에 대한 HMAC-SHA256 서명 hex. */
    public String computeSignature(MarkerContent content) {
        String canonical = canonicalJson(content);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new MarkerWriteFailedException("HMAC 계산 실패", e);
        }
    }

    /** {@code content.signature} 가 현재 서버 키로 재계산한 값과 일치하는지. 외부 이식/변조 감지. */
    public boolean verifySignature(MarkerContent content) {
        String expected = computeSignature(content);
        return constantTimeEquals(expected, content.signature());
    }

    /** 저장된 {@code manifestHash} 와 caller 가 자원에서 재계산한 해시가 일치하는지. */
    public boolean verifyManifestHash(MarkerContent content, String recomputedHash) {
        return constantTimeEquals(content.manifestHash(), recomputedHash);
    }

    /**
     * signature 를 제외한 canonical JSON. 필드 순서가 결정적으로 나오도록 record 구성 순서를 그대로 따른다.
     * ObjectMapper 가 record component 순서를 보존하므로 별도 정렬 불필요.
     */
    private String canonicalJson(MarkerContent content) {
        try {
            return om.writeValueAsString(content.withoutSignature());
        } catch (RuntimeException e) {
            throw new MarkerWriteFailedException("marker canonical JSON 직렬화 실패", e);
        }
    }

    /** 타이밍 공격 대비 상수시간 비교. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return a == b;
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}

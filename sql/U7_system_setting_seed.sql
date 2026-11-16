-- U7 배포 DML. 프로비저닝 허용 대역 초기값(10.1.1.0/24, 접근 권한 최고 관리자).
-- 선행: ddl/U7_system_setting.sql. 이 DML 을 빠뜨리면 모든 서버의 개시가 막힌다(허용 대역 미설정).
-- INSERT IGNORE: 같은 키의 행이 이미 있으면 건너뛴다. 다시 실행해도 화면에서 바꾼 값을 덮지 않는다.
-- 확인: SELECT setting_key, value_json, min_role FROM system_setting;
INSERT IGNORE INTO system_setting (created_at, updated_at, setting_key, value_json, min_role)
VALUES (NOW(6), NOW(6), 'PROVISIONING_ALLOWED_RANGE',
        '{"type":"PROVISIONING_ALLOWED_RANGE","cidr":"10.1.1.0/24"}', 'SUPER_ADMIN');

-- S23-1-a — OS 미지원 상수 제거: 두 컬럼의 ENUM 에서 옛 값(ROCKY_LINUX, CENTOS, WINDOWS, WINDOWS_SERVER)을 지운다.
-- 적용: S23-1-a jar 와 같은 순간에. 반드시 docs/deploy/pending/S23-1.md 의 점검 SQL ① ② 합계가 0 일 때.
--
-- MariaDB 는 ENUM 에서 값을 지우면 그 값을 가진 행을 오류 없이 빈 문자열로 바꾼다. 그래서 아래 가드가 미지원 값이 하나라도
-- 남아 있으면 SIGNAL 로 멈추고, 그때는 ALTER 가 실행되지 않는다(익명 블록, MariaDB 10.1+).
-- mariadb 클라이언트로 적용할 때는 DELIMITER 를 바꿔 블록을 한 문장으로 보낸다.

DELIMITER //
BEGIN NOT ATOMIC
  DECLARE remaining INT;
  SELECT (SELECT COUNT(*) FROM `os_metadata` WHERE `os_name` IN ('ROCKY_LINUX','CENTOS','WINDOWS','WINDOWS_SERVER'))
       + (SELECT COUNT(*) FROM `subprogram`  WHERE `os_name` IN ('ROCKY_LINUX','CENTOS','WINDOWS','WINDOWS_SERVER'))
    INTO remaining;
  IF remaining > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'S23-1-a: 미지원 OS 값이 남아 있어 ENUM 을 줄이지 않는다. 점검 SQL 을 다시 확인할 것';
  END IF;

  ALTER TABLE `os_metadata`
    MODIFY `os_name` ENUM('ROCKY_LINUX_8','ROCKY_LINUX_9','ROCKY_LINUX_10',
                          'RHEL_8','RHEL_9','RHEL_10',
                          'WINDOWS_SERVER_2025','UBUNTU') NOT NULL;

  ALTER TABLE `subprogram`
    MODIFY `os_name` ENUM('ROCKY_LINUX_8','ROCKY_LINUX_9','ROCKY_LINUX_10',
                          'RHEL_8','RHEL_9','RHEL_10',
                          'WINDOWS_SERVER_2025','UBUNTU') DEFAULT NULL;
END //
DELIMITER ;

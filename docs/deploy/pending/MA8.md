# MA8 최고 관리자와 사용자 관리

dev 병합 `997ce9b`. 로컬 개발 DB(3306)에는 적용했다.

## DDL 과 DML

- 파일: `ddl/MA8_super_admin.sql`
- 적용 시점: jar 와 같은 순간. 파일 하나를 통째로 적용한다. `users` 의 등록 시각 컬럼이 없으면 새 jar 가 기동을 거절하므로 jar 만 먼저 올라가는 일은 없다.
- 하는 일: `users` 에 등록 시각과 수정 시각 컬럼을 더하고, `users_role.role` 을 다시 선언한 뒤, 지금 관리자 권한을 가진 사용자 모두에게 최고 관리자 권한 행을 더한다.
- 다시 적용해도 안전하다.
- 확인 SQL:

```sql
SHOW CREATE TABLE users;
SELECT role, COUNT(*) FROM users_role GROUP BY role;
```

## 되돌릴 때

MA8 이전 jar 로 되돌리려면 구 jar 를 띄우기 전에 `ddl/MA8_super_admin_rollback.sql` 을 적용한다. 구 jar 는 최고 관리자 권한 값을 읽지 못해 승격된 관리자가 로그인하지 못한다. DELETE 권한이 있는 계정으로 적용한다.

## 사용자 결정

배포 직후에는 기존 관리자 전원이 최고 관리자다. 사용자 관리 화면에서 최고 관리자로 남길 계정을 줄인다.

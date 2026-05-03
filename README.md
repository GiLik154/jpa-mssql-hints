# jpa-mssql-hints

Spring Boot + JPA(Hibernate) + MS-SQL 환경에서 `WITH (NOLOCK)` / `READPAST` /
`UPDLOCK` / `ROWLOCK` 등 테이블 힌트를 **어노테이션과 설정**으로 적용하는
Spring Boot Starter 라이브러리입니다.

> ⚠️ **먼저 RCSI(`READ_COMMITTED_SNAPSHOT ON`)를 검토하세요.** 대부분의
> 데드락/블로킹은 RCSI로 해결되며, `NOLOCK`은 사실상 `READ UNCOMMITTED`라
> dirty/phantom read를 허용합니다. 이 라이브러리는 RCSI를 켤 수 없는
> 레거시·SI·금융 환경 또는 통계/리포트 전용 경로를 위한 도구입니다.

## 지원 환경

| 구성 요소 | 버전 |
|---|---|
| Java | 17+ |
| Spring Boot | 3.2.x ~ 3.4.x |
| Hibernate | 6.x |
| MS-SQL | 2017+ |

> Spring Boot 2.x / Java 8 / Hibernate 5는 지원하지 않습니다.
> `jakarta.*` ↔ `javax.*` 분기 처리, Hibernate SPI 패키지 이동 비용이 커
> 1인 OSS의 유지 범위를 벗어나기 때문입니다.

## 설치

```kotlin
dependencies {
    implementation("io.github.jpamssqlhints:jpa-mssql-hints:0.1.0")
}
```

사용처에 `spring-boot-autoconfigure`, `spring-data-jpa`,
`spring-boot-starter-aop`, `hibernate-core`가 이미 있어야 합니다
(라이브러리 자체는 모두 `compileOnly`로만 의존).

## 사용

### 1. `@NoLock` (단일 NOLOCK)

```java
@Service
public class ReportService {

    @NoLock
    @Transactional(readOnly = true)
    public List<DailyReport> findDailyReports(LocalDate from, LocalDate to) {
        return reportRepository.findByDateBetween(from, to);
    }
}
```

### 2. `@TableHint` (다양한 힌트 / 복수 결합)

```java
@TableHint(Hint.READPAST)
public List<Item> findAvailable() { ... }                   // WITH (READPAST)

@TableHint({Hint.UPDLOCK, Hint.ROWLOCK})
public Item lockForUpdate(Long id) { ... }                  // WITH (UPDLOCK, ROWLOCK)

@TableHint(Hint.NOLOCK)                                     // @NoLock과 동일
public List<Stat> findStats() { ... }
```

클래스 단위 적용:

```java
@NoLock
@Repository
public class StatisticsReadOnlyDao { ... }
```

## 설정 옵션

```yaml
jpa-mssql-hints:
  enabled: true              # false면 AutoConfiguration 비활성
  mode: annotation           # annotation | global | off
  exclude-tables:            # NOLOCK 절대 적용 X (블랙리스트, glob 지원)
    - payment_*
    - "*_audit"
  always-apply-tables:       # 어노테이션 없어도 NOLOCK 적용 (화이트리스트)
    - dashboard_*
    - stat_*
  require-read-only: false   # true면 readOnly 트랜잭션에서만 적용 (안전장치)
  log-transformed-sql: false # true면 변환 전/후 SQL을 INFO로 로깅
```

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `enabled` | `true` | 전체 활성/비활성 |
| `mode` | `annotation` | `annotation`(어노테이션만) / `global`(모든 SELECT) / `off`(kill switch) |
| `exclude-tables` | `[]` | 적용 제외할 테이블 (glob: `stat_*`, `*_log`) |
| `always-apply-tables` | `[]` | 어노테이션과 무관하게 항상 적용 |
| `require-read-only` | `false` | 쓰기 트랜잭션에서 NOLOCK 거부 (정합성 보호) |
| `log-transformed-sql` | `false` | 운영 도입 시 발급 SQL 검증용 |

### 우선순위

테이블 단위 적용 여부 결정 순서:

1. **블랙리스트 매칭** → 무조건 미적용 (안전 우선)
2. **화이트리스트 매칭** → 무조건 적용 (어노테이션 없어도)
3. **모드 + 컨텍스트** 기본값
4. `require-read-only=true`이고 readOnly 트랜잭션이 아니면 위 결과와 무관하게 미적용
5. `mode=off`면 화이트리스트도 무시 (kill switch)

## 동작 방식

1. `@NoLock` / `@TableHint` 메서드 진입 시 AOP가 `HintContext`(스레드별 스택)에
   힌트 push
2. Hibernate `StatementInspector`가 SQL 발급 직전 가로챔
3. 활성 힌트 + 모드 + 화이트/블랙 매칭으로 적용 여부 결정
4. SELECT의 `FROM` / `JOIN` 뒤에 `WITH (...)` 삽입
5. 메서드 종료 시 (예외 포함) `finally`로 컨텍스트 정리

EntityManager / DataSource를 주입받지 않습니다. 사용처의 Hibernate에
`hibernate.session_factory.statement_inspector` 프로퍼티로만 후킹합니다.

## 한계

- 정규식 기반 SQL 재작성이라 다음은 보장하지 않습니다:
  - 복잡한 CTE (`WITH cte AS (...) SELECT ...`)
  - 문자열 리터럴 안의 `from` 키워드
  - 이미 다른 테이블 힌트가 붙은 쿼리
- `@Async` / `@Transactional(propagation = REQUIRES_NEW)` 등 별도 스레드로
  넘어가는 경우 ThreadLocal이 끊깁니다 (Spring 트랜잭션과 동일 한계).
  `TaskDecorator`로 전파 처리 필요.
- NOLOCK은 dirty read 허용 — `@Transactional(readOnly = true)` 또는
  `require-read-only: true`와 함께 쓰는 것을 강력 권장.
- 운영 도입 첫 주는 `log-transformed-sql: true`로 발급 SQL 검증 권장.

## 로드맵

- [ ] `@DeadlockRetry` (1205 SQLException 자동 재시도)
- [ ] `@IsolationLevel(SNAPSHOT)` 격리 수준 어노테이션
- [ ] Micrometer 메트릭 (`nolock.applied.total{table}`)
- [ ] SQL 파서 기반 안전한 재작성 (jSqlParser 도입 검토)

## 라이선스

MIT

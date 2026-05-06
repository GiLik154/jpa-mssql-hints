# jpa-mssql-hints

[![CI](https://github.com/GiLik154/jpa-mssql-hints/actions/workflows/ci.yml/badge.svg)](https://github.com/GiLik154/jpa-mssql-hints/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/GiLik154/jpa-mssql-hints?sort=semver)](https://github.com/GiLik154/jpa-mssql-hints/releases)
[![JitPack](https://jitpack.io/v/GiLik154/jpa-mssql-hints.svg)](https://jitpack.io/#GiLik154/jpa-mssql-hints)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)

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

> Maven Central은 1.0 이후 등록 예정. 그 전까지는 [JitPack](https://jitpack.io/)을 통해 설치하세요.

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.GiLik154:jpa-mssql-hints:v1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.GiLik154</groupId>
    <artifactId>jpa-mssql-hints</artifactId>
    <version>v1.0.0</version>
</dependency>
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

**메서드 vs 클래스 어노테이션 우선순위**

- 메서드 어노테이션이 클래스 어노테이션을 **덮어쓴다**.
- 메서드에 빈 `@TableHint({})`를 달면 **클래스 단위 적용을 끌 수 있다** (어떤 힌트도 적용하지 않음).

```java
@TableHint(Hint.NOLOCK)
class FooDao {
    @TableHint(Hint.READPAST)
    Foo a() { ... }   // READPAST만 (NOLOCK 무시)

    @TableHint({})
    Foo b() { ... }   // 어떤 힌트도 적용 안 됨 (클래스 NOLOCK 끔)

    Foo c() { ... }   // NOLOCK (클래스 어노테이션 적용)
}
```

## 설정 옵션

> **opt-in 모델**: 스타터를 추가만 해도 자동 활성화되지 않습니다.
> `jpa-mssql-hints.enabled=true`를 명시해야 빈이 등록됩니다 — 의도치 않은
> 활성화를 막기 위한 보안 디폴트입니다.

```yaml
jpa-mssql-hints:
  enabled: true              # 반드시 명시해야 활성화 (opt-in)
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
| `enabled` | `true` | AutoConfiguration 자체 활성/비활성. `false`면 빈 자체가 등록되지 않음 |
| `mode` | `annotation` | `annotation`(어노테이션만) / `global`(모든 SELECT) / `off`(빈은 살리고 변환만 끔) |
| `exclude-tables` | `[]` | 적용 제외할 테이블 (glob: `stat_*`, `*_log`) |
| `always-apply-tables` | `[]` | 어노테이션과 무관하게 항상 적용 |
| `require-read-only` | `false` | readOnly 트랜잭션에서만 적용. **트랜잭션 부재 시에도 거부** (정합성 보호) |
| `log-transformed-sql` | `false` | 운영 도입 시 발급 SQL 검증용 |

### 우선순위

테이블 단위 적용 여부 결정 순서:

1. **블랙리스트 매칭** → 무조건 미적용 (안전 우선)
2. **화이트리스트 매칭** → 무조건 적용 (어노테이션 없어도)
3. **모드 + 컨텍스트** 기본값
4. `require-read-only=true`이고 readOnly 트랜잭션이 아니면 위 결과와 무관하게 미적용
5. `mode=off`면 화이트리스트도 무시 (kill switch)

> **`require-read-only` 동작 주의**: `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`
> 기준이라 **트랜잭션이 없는 SELECT(예: OSIV off 환경의 직접 Repository 호출)도 거부**됩니다.
> 안전 측 디폴트지만, "readOnly가 아닌 트랜잭션만 막는다"가 아니라는 점에 유의하세요.

> **테이블 이름 비교는 schema-less**입니다. `excludeTables: ["dbo.audit_log"]`처럼 스키마를
> 포함해 적으면 실제로는 `audit_log`만 비교 대상에 들어가 의도한 매칭이 일어나지 않습니다.
> 시작 단계에서 `.` / `[` / `]`가 포함된 패턴이 발견되면 WARN 로그가 출력됩니다.

> **`mode=off` vs `enabled=false`**:
> - `enabled=false` — AutoConfiguration 자체가 비활성. 빈/StatementInspector가 등록되지 않음. **영구 비활성/제거** 용도.
> - `mode=off` — 빈은 살리고 변환만 끔. 환경변수만 바꿔 즉시 토글 가능. **운영 중 사고 대응(kill switch)** 용도.

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
- **단일 SQL에 사용자가 직접 넣은 힌트가 하나라도 있으면 전체 변환을 건너뜁니다.**
  Native query 보호 정책. 일부 테이블에만 힌트를 넣은 경우 다른 테이블에는
  힌트가 추가되지 않습니다.
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

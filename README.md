# jpa-mssql-hints

Spring Boot + JPA(Hibernate) + MS-SQL 환경에서 `WITH (NOLOCK)` 등 테이블 힌트를
**어노테이션 한 줄로** 적용할 수 있도록 돕는 라이브러리입니다.

> ⚠️ 이 라이브러리를 쓰기 전에 **먼저 RCSI(`READ_COMMITTED_SNAPSHOT ON`)를
> 검토하세요.** 대부분의 데드락/블로킹은 RCSI로 해결되며, `NOLOCK`은
> 사실상 `READ UNCOMMITTED`라 dirty/phantom read를 허용합니다.

## 언제 쓰나

- DBA 권한이 없거나 운영 정책상 RCSI를 켤 수 없는 환경
- 통계/리포트 등 **정확성보다 가용성이 중요한 읽기 경로**
- 특정 핫 테이블에서만 한정적으로 락 회피가 필요할 때

정합성이 중요한 결제·주문 같은 경로에는 쓰지 마세요.

## 설치

```kotlin
dependencies {
    implementation("io.github.jpamssqlhints:jpa-mssql-hints:0.1.0")
}
```

`spring-boot-autoconfigure`, `spring-data-jpa`, `spring-boot-starter-aop`,
`hibernate-core`가 사용처에 이미 있어야 합니다 (compileOnly).

## 사용

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

`@NoLock`이 붙은 메서드 안에서 발급되는 모든 `SELECT` SQL의 `FROM`/`JOIN`
뒤에 `WITH (NOLOCK)`이 자동으로 주입됩니다.

클래스 단위 적용:

```java
@NoLock
@Repository
public class StatisticsReadOnlyDao { ... }
```

## 비활성화

```yaml
jpa-mssql-hints:
  enabled: false
```

## 동작 방식

1. `NoLockAspect`가 `@NoLock` 메서드 진입 시 ThreadLocal 카운터 +1
2. Hibernate `StatementInspector`가 발급 직전 SQL을 가로채 `SELECT`이고
   ThreadLocal이 활성이면 `WITH (NOLOCK)` 삽입
3. 메서드 종료 시 카운터 -1, 0이 되면 ThreadLocal 제거

EntityManager / DataSource를 주입받지 않습니다. 사용처의 Hibernate에
`hibernate.session_factory.statement_inspector` 프로퍼티로 후킹합니다.

## 한계

- 정규식 기반 SQL 재작성이라 다음은 보장하지 않습니다:
  - 복잡한 CTE / 중첩 서브쿼리
  - 이미 다른 테이블 힌트가 붙은 쿼리
  - 비표준 식별자(따옴표/대괄호 혼용)
- 운영 도입 전 `org.hibernate.SQL` 로그를 켜고 발급 SQL을 검증하세요.
- `@Async` / `@Transactional(propagation = REQUIRES_NEW)` 등 별도 스레드로
  넘어가는 경우 ThreadLocal이 끊깁니다. `TaskDecorator`로 전파 처리가 필요합니다.
- 트랜잭션이 쓰기 모드면 `NOLOCK`이 의미가 없거나 위험할 수 있습니다.
  `@Transactional(readOnly = true)`와 함께 쓰세요.

## 로드맵

- [ ] `READPAST`, `UPDLOCK`, `ROWLOCK` 힌트 지원
- [ ] 격리 수준 어노테이션 (`@IsolationLevel(SNAPSHOT)`)
- [ ] 데드락 자동 재시도 (`@DeadlockRetry`)
- [ ] SQL 파서 기반 안전한 재작성 (jSqlParser 도입 검토)

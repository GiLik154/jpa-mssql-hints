# Changelog

이 프로젝트의 모든 주요 변경사항을 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)을 따르고,
버전은 [Semantic Versioning](https://semver.org/lang/ko/)을 따릅니다.

## [0.1.0] - 2026-05-03

첫 안정 릴리즈.

### Added

#### 어노테이션 / 컨텍스트
- `@NoLock` — 메서드/클래스 구간에 `WITH (NOLOCK)` 자동 주입
- `@TableHint(Hint...)` — 일반화된 힌트 어노테이션. `NOLOCK` / `READPAST` / `UPDLOCK` / `ROWLOCK` + 복수 결합
- `@NoLock` / `@TableHint` 메타: `@Inherited` + `@Documented` (추상 부모 클래스 어노테이션 상속)
- `Hint.regexAlternation()` — enum에서 정규식 alternation 동적 생성 (OCP 준수)
- `HintContext.open(Set<Hint>)` — `try-with-resources` 안전 스코프 (`AutoCloseable`)
- `HintContext` — `ThreadLocal` 스택 기반, 중첩 호출/예외 발생 시 자동 정리

#### AOP / Inspector
- 단일 `TableHintAspect`가 `@TableHint`와 `@NoLock`을 모두 처리
- AOP pointcut에서 `java.lang.Object` 메서드 가로채기 자동 제외
- Hibernate `StatementInspector` SPI 후킹 — 영속성 컨텍스트와 비결합
- `FROM` / `JOIN` 정규식을 단일 패스로 통합 (성능)

#### 외부 설정 (`jpa-mssql-hints.*`)
- `enabled` — opt-in (기본 `false`, 명시적으로 `true`로 켜야 활성화)
- `mode` — `annotation` / `global` / `off`
- `exclude-tables` — 블랙리스트 (glob 패턴 지원, schema-qualified 사용 시 시작 단계 WARN)
- `always-apply-tables` — 화이트리스트 (glob 패턴 지원)
- `require-read-only` — 쓰기 트랜잭션 / 트랜잭션 부재 시 적용 거부
- `log-transformed-sql` — 변환 전/후 SQL 로깅
- `log-transformed-sql-max-length` — 로그 SQL 길이 제한 (PII/Secret 누출 방지)
- `max-sql-length` — 매우 긴 SQL 입력에 대한 ReDoS 방어 가드

#### 기타
- Spring Boot AutoConfiguration (`AutoConfiguration.imports`)
- `NoLockStatementInspector.builder()` — 옵션 추가에 telescope 없이 확장 가능
- 152개 단위·통합 테스트 (정규식 엣지 케이스, RESERVED 키워드, 다중 JOIN, AOP 통합, ReDoS 가드, 어노테이션 우선순위 등)

### 우선순위 정책

1. 블랙리스트 매칭 → 무조건 미적용
2. 화이트리스트 매칭 → 무조건 적용
3. 모드/컨텍스트 기본값
4. `require-read-only=true` + readOnly 트랜잭션 아님 → 미적용
5. `mode=off` → 화이트리스트도 무시 (kill switch)

### 어노테이션 우선순위

- 메서드 어노테이션이 클래스 어노테이션을 덮어쓴다.
- 메서드에 빈 `@TableHint({})`를 달면 클래스 단위 적용을 끌 수 있다 (어떤 힌트도 박지 않음).

### 알려진 한계

- CTE (`WITH ... AS`) 미지원
- 문자열 리터럴 안의 `from` 구분 불가 (정규식 한계)
- 별도 스레드(`@Async` 등)로 컨텍스트 전파 안 됨

[0.1.0]: https://github.com/GiLik154/jpa-mssql-hints/releases/tag/v0.1.0

# Changelog

이 프로젝트의 모든 주요 변경사항을 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)을 따르고,
버전은 [Semantic Versioning](https://semver.org/lang/ko/)을 따릅니다.

## [Unreleased]

## [0.1.0] - 2026-05-03

### Added

- `@NoLock` 어노테이션 — 메서드/클래스 구간에 `WITH (NOLOCK)` 자동 주입
- `@TableHint(Hint...)` 일반화 — `NOLOCK`, `READPAST`, `UPDLOCK`, `ROWLOCK` 및 복수 결합 지원
- 외부 설정(`JpaMssqlHintsProperties`):
  - `mode`: `annotation` / `global` / `off`
  - `exclude-tables`: 블랙리스트 (glob 패턴 지원)
  - `always-apply-tables`: 화이트리스트 (glob 패턴 지원)
  - `require-read-only`: 쓰기 트랜잭션에서 적용 거부
  - `log-transformed-sql`: 변환 전/후 SQL 로깅
- Hibernate `StatementInspector` SPI 후킹으로 영속성 컨텍스트와 비결합
- Spring Boot AutoConfiguration (`spring.factories` 대체 imports 파일 사용)
- `ThreadLocal` 스택 기반 `HintContext` — 중첩 호출/예외 발생 시 자동 정리
- 132개 단위·통합 테스트 (정규식 엣지 케이스, RESERVED 키워드, 다중 JOIN, AOP 통합 포함)

### 우선순위 정책

1. 블랙리스트 매칭 → 무조건 미적용
2. 화이트리스트 매칭 → 무조건 적용
3. 모드/컨텍스트 기본값
4. `require-read-only=true` + readOnly 아님 → 미적용
5. `mode=off` → 화이트리스트도 무시 (kill switch)

### 알려진 한계

- CTE (`WITH ... AS`) 미지원
- 문자열 리터럴 안의 `from` 구분 불가 (정규식 한계)
- 별도 스레드(`@Async` 등)로 컨텍스트 전파 안 됨

[Unreleased]: https://github.com/GiLik154/jpa-mssql-hints/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/GiLik154/jpa-mssql-hints/releases/tag/v0.1.0

package io.github.jpamssqlhints.config;

/**
 * NOLOCK 적용 정책.
 *
 * <ul>
 *   <li>{@link #ANNOTATION} (기본): @NoLock이 붙은 메서드(또는 클래스) 구간에서만 적용</li>
 *   <li>{@link #GLOBAL}: 어노테이션 없어도 모든 SELECT에 적용. 통계/리포트 전용 DataSource에 권장</li>
 *   <li>{@link #OFF}: 라이브러리는 활성이지만 어떤 변환도 하지 않음. 운영 중 즉시 비활성용</li>
 * </ul>
 */
public enum Mode {
    ANNOTATION,
    GLOBAL,
    OFF
}

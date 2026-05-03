package io.github.jpamssqlhints.annotation;

/**
 * MS-SQL 테이블 힌트 종류. SQL 표현 자체는 enum 이름과 동일.
 *
 * <ul>
 *   <li>{@link #NOLOCK}: READ UNCOMMITTED 등가, dirty/phantom read 허용</li>
 *   <li>{@link #READPAST}: 락 걸린 행은 건너뛰고 읽음</li>
 *   <li>{@link #UPDLOCK}: 읽는 시점에 update 락 획득</li>
 *   <li>{@link #ROWLOCK}: 페이지/테이블 락 대신 행 단위 락 강제</li>
 * </ul>
 */
public enum Hint {
    NOLOCK,
    READPAST,
    UPDLOCK,
    ROWLOCK
}

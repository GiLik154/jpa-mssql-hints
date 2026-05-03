package io.github.jpamssqlhints.annotation;

import java.util.Arrays;
import java.util.stream.Collectors;

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
    ROWLOCK;

    /** SQL의 {@code WITH (...)} 안에 들어갈 토큰. enum 이름과 동일하지만 의도를 명확히 한다. */
    public String sqlToken() {
        return name();
    }

    /**
     * 모든 힌트 토큰을 묶은 정규식 alternation. (예: {@code "(?:nolock|readpast|updlock|rowlock)"})
     * <p>새 enum 값 추가 시 여기서 자동 반영되므로, 정규식 사용처에서 하드코딩하면 안 된다.
     */
    public static String regexAlternation() {
        return Arrays.stream(values())
                .map(h -> h.sqlToken().toLowerCase())
                .collect(Collectors.joining("|", "(?:", ")"));
    }
}

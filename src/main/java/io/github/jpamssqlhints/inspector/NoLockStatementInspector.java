package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.config.Mode;
import io.github.jpamssqlhints.context.NoLockContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.regex.Pattern;

/**
 * Hibernate가 SQL을 발급하기 직전에 호출되는 SPI 구현체. 현재 스레드가
 * NoLock 활성 상태이고 SELECT 문일 때만 FROM/JOIN 뒤에 WITH (NOLOCK)을
 * 삽입합니다.
 *
 * <p>한계: 정규식 기반이므로 복잡한 CTE/서브쿼리/이미 다른 힌트가 붙은
 * 쿼리는 보장하지 않습니다. 운영 도입 전에 발급 SQL을 로깅으로 검증하세요.
 */
public class NoLockStatementInspector implements StatementInspector {

    private final Mode mode;

    public NoLockStatementInspector() {
        this(Mode.ANNOTATION);
    }

    public NoLockStatementInspector(Mode mode) {
        this.mode = mode;
    }

    private static final Pattern SELECT_HEAD = Pattern.compile("^\\s*select\\b", Pattern.CASE_INSENSITIVE);

    /** alias 자리에 와선 안 되는 SQL 키워드. 이게 다음 토큰이면 alias 없는 것으로 본다. */
    private static final String RESERVED = "(?:where|group|order|having|inner|left|right|full|cross|outer|join|on|union|intersect|except|limit|offset|fetch|with|for|option|set)\\b";

    private static final Pattern FROM_TABLE = Pattern.compile(
            "(\\bfrom\\s+\\[?\\w+]?(?:\\.\\[?\\w+]?)?(?:\\s+(?:as\\s+)?(?!" + RESERVED + ")\\w+)?)(?!\\s*with\\s*\\(\\s*nolock\\s*\\))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JOIN_TABLE = Pattern.compile(
            "(\\bjoin\\s+\\[?\\w+]?(?:\\.\\[?\\w+]?)?(?:\\s+(?:as\\s+)?(?!" + RESERVED + ")\\w+)?)(?!\\s*with\\s*\\(\\s*nolock\\s*\\))",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String inspect(String sql) {
        if (sql == null || !shouldApply()) {
            return sql;
        }
        if (!SELECT_HEAD.matcher(sql).find()) {
            return sql;
        }
        // 이미 NOLOCK이 적용된 SQL은 추가 변환하지 않는다. 부분 매치로 인한
        // 중복 삽입 위험을 피하고 사용처가 직접 작성한 native query를 보호.
        String lower = sql.toLowerCase();
        if (lower.contains("with (nolock)") || lower.contains("with(nolock)")) {
            return sql;
        }
        String result = appendHint(sql, FROM_TABLE);
        result = appendHint(result, JOIN_TABLE);
        return result;
    }

    /**
     * 모드별 적용 여부 결정.
     * <ul>
     *   <li>OFF: 항상 미적용 (kill switch)</li>
     *   <li>GLOBAL: 어노테이션과 무관하게 항상 적용</li>
     *   <li>ANNOTATION: NoLockContext가 활성일 때만 적용</li>
     * </ul>
     */
    private boolean shouldApply() {
        return switch (mode) {
            case OFF -> false;
            case GLOBAL -> true;
            case ANNOTATION -> NoLockContext.isActive();
        };
    }

    private String appendHint(String sql, Pattern pattern) {
        return pattern.matcher(sql).replaceAll("$1 WITH (NOLOCK)");
    }
}

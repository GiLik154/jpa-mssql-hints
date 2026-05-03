package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.annotation.Hint;
import io.github.jpamssqlhints.config.Mode;
import io.github.jpamssqlhints.context.HintContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hibernate가 SQL을 발급하기 직전에 호출되는 SPI 구현체. 현재 스레드가
 * NoLock 활성 상태이고 SELECT 문일 때만 FROM/JOIN 뒤에 WITH (NOLOCK)을
 * 삽입합니다.
 *
 * <p>한계: 정규식 기반이므로 복잡한 CTE/서브쿼리/이미 다른 힌트가 붙은
 * 쿼리는 보장하지 않습니다. 운영 도입 전에 발급 SQL을 로깅으로 검증하세요.
 */
public class NoLockStatementInspector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(NoLockStatementInspector.class);

    private final Mode mode;
    private final TablePatternMatcher excludeTables;
    private final TablePatternMatcher alwaysApplyTables;
    private final boolean requireReadOnly;
    private final boolean logTransformedSql;

    public NoLockStatementInspector() {
        this(Mode.ANNOTATION, Collections.emptyList(), Collections.emptyList(), false, false);
    }

    public NoLockStatementInspector(Mode mode) {
        this(mode, Collections.emptyList(), Collections.emptyList(), false, false);
    }

    public NoLockStatementInspector(Mode mode, List<String> excludeTables) {
        this(mode, excludeTables, Collections.emptyList(), false, false);
    }

    public NoLockStatementInspector(Mode mode, List<String> excludeTables, List<String> alwaysApplyTables) {
        this(mode, excludeTables, alwaysApplyTables, false, false);
    }

    public NoLockStatementInspector(Mode mode, List<String> excludeTables, List<String> alwaysApplyTables, boolean requireReadOnly) {
        this(mode, excludeTables, alwaysApplyTables, requireReadOnly, false);
    }

    public NoLockStatementInspector(Mode mode, List<String> excludeTables, List<String> alwaysApplyTables,
                                    boolean requireReadOnly, boolean logTransformedSql) {
        this.mode = mode;
        this.excludeTables = new TablePatternMatcher(excludeTables);
        this.alwaysApplyTables = new TablePatternMatcher(alwaysApplyTables);
        this.requireReadOnly = requireReadOnly;
        this.logTransformedSql = logTransformedSql;
    }

    private static final Pattern SELECT_HEAD = Pattern.compile("^\\s*select\\b", Pattern.CASE_INSENSITIVE);

    /** alias 자리에 와선 안 되는 SQL 키워드. 이게 다음 토큰이면 alias 없는 것으로 본다. */
    private static final String RESERVED = "(?:where|group|order|having|inner|left|right|full|cross|outer|join|on|union|intersect|except|limit|offset|fetch|with|for|option|set)\\b";

    /**
     * 정규식 그룹 구조:
     * <ul>
     *   <li>group(1): "from " / "join " 키워드 + 공백</li>
     *   <li>group(2): 테이블 식별자 (스키마.테이블 또는 [테이블] 가능)</li>
     *   <li>group(3): alias 부분 (있을 때) — 비어있을 수 있음</li>
     * </ul>
     */
    private static final Pattern FROM_TABLE = Pattern.compile(
            "(\\bfrom\\s+)(\\[?\\w+]?(?:\\.\\[?\\w+]?)?)((?:\\s+(?:as\\s+)?(?!" + RESERVED + ")\\w+)?)(?!\\s*with\\s*\\(\\s*nolock\\s*\\))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JOIN_TABLE = Pattern.compile(
            "(\\bjoin\\s+)(\\[?\\w+]?(?:\\.\\[?\\w+]?)?)((?:\\s+(?:as\\s+)?(?!" + RESERVED + ")\\w+)?)(?!\\s*with\\s*\\(\\s*nolock\\s*\\))",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String inspect(String sql) {
        // OFF 모드는 화이트리스트도 무시하는 절대 kill switch.
        if (sql == null || mode == Mode.OFF) {
            return sql;
        }
        if (!SELECT_HEAD.matcher(sql).find()) {
            return sql;
        }
        // requireReadOnly=true면 readOnly 트랜잭션 안에서만 적용. 쓰기/무트랜잭션은 거부.
        if (requireReadOnly && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return sql;
        }
        boolean defaultActive = isDefaultActive();
        // 모드 기반 비활성 + 화이트리스트도 비어있으면 어떤 테이블도 적용 대상 아님.
        if (!defaultActive && alwaysApplyTables.isEmpty()) {
            return sql;
        }
        // 이미 어떤 형태의 WITH 힌트가 박혀 있으면 추가 변환하지 않는다.
        // native query 보호 + 부분 매치로 인한 중복 삽입 위험 회피.
        if (alreadyHasHint(sql)) {
            return sql;
        }
        String hintExpr = buildHintExpression();
        String result = appendHint(sql, FROM_TABLE, defaultActive, hintExpr);
        result = appendHint(result, JOIN_TABLE, defaultActive, hintExpr);
        if (logTransformedSql && !result.equals(sql)) {
            log.info("[jpa-mssql-hints] before: {}", sql);
            log.info("[jpa-mssql-hints] after : {}", result);
        }
        return result;
    }

    /**
     * 모드/컨텍스트 기반 기본 적용 여부.
     * (OFF는 inspect 진입 시점에 이미 걸러진다)
     */
    private boolean isDefaultActive() {
        return switch (mode) {
            case OFF -> false;
            case GLOBAL -> true;
            case ANNOTATION -> HintContext.isActive();
        };
    }

    /**
     * 적용할 힌트 표현식 결정.
     * <ul>
     *   <li>HintContext가 비어있으면 기본 NOLOCK (GLOBAL/whitelist 호환성)</li>
     *   <li>HintContext가 채워져 있으면 그 힌트들을 enum 순서대로 결합</li>
     * </ul>
     */
    private String buildHintExpression() {
        Set<Hint> ctx = HintContext.currentHints();
        EnumSet<Hint> hints = ctx.isEmpty() ? EnumSet.of(Hint.NOLOCK) : EnumSet.copyOf(ctx);
        return "WITH (" + hints.stream().map(Enum::name).collect(Collectors.joining(", ")) + ")";
    }

    private boolean alreadyHasHint(String sql) {
        String lower = sql.toLowerCase();
        // 가장 흔한 케이스 빠른 검사 + 일반화: with ( anything )
        return lower.contains("with (nolock)") || lower.contains("with(nolock)")
                || HINT_PRESENT.matcher(sql).find();
    }

    private static final Pattern HINT_PRESENT = Pattern.compile(
            "\\bwith\\s*\\(\\s*(?:nolock|readpast|updlock|rowlock)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 테이블별 적용 여부:
     * <ol>
     *   <li>블랙리스트 매칭 → 무조건 미적용 (안전 우선)</li>
     *   <li>화이트리스트 매칭 → 무조건 적용 (어노테이션 없어도)</li>
     *   <li>그 외 → 모드/컨텍스트 기본값(defaultActive)</li>
     * </ol>
     */
    private String appendHint(String sql, Pattern pattern, boolean defaultActive, String hintExpr) {
        Matcher m = pattern.matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String tableName = extractTableName(m.group(2));
            String original = m.group();
            boolean apply;
            if (excludeTables.matches(tableName)) {
                apply = false;
            } else if (alwaysApplyTables.matches(tableName)) {
                apply = true;
            } else {
                apply = defaultActive;
            }
            String replacement = apply ? original + " " + hintExpr : original;
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * "dbo.member" / "[dbo].[member]" / "member" → "member" (소문자)
     */
    private String extractTableName(String tableExpr) {
        String trimmed = tableExpr.trim();
        int dotIdx = trimmed.lastIndexOf('.');
        String last = dotIdx >= 0 ? trimmed.substring(dotIdx + 1) : trimmed;
        return last.replace("[", "").replace("]", "").toLowerCase();
    }
}

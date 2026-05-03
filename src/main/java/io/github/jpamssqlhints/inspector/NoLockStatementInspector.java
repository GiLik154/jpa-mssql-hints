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

    /**
     * 변환 SQL 전용 로거. 사용처에서 {@code logging.level.io.github.jpamssqlhints.SQL=DEBUG}로
     * 끄고 켤 수 있도록 클래스 로거와 분리.
     */
    private static final Logger SQL_LOG = LoggerFactory.getLogger("io.github.jpamssqlhints.SQL");

    /** 클래스 로거 — 시작 시점 진단(WARN 등)에 사용. */
    private static final Logger log = LoggerFactory.getLogger(NoLockStatementInspector.class);

    private static final Pattern SELECT_HEAD = Pattern.compile("^\\s*select\\b", Pattern.CASE_INSENSITIVE);

    /** alias 자리에 와선 안 되는 SQL 키워드. 이게 다음 토큰이면 alias 없는 것으로 본다. */
    private static final String RESERVED = "(?:where|group|order|having|inner|left|right|full|cross|outer|join|on|union|intersect|except|limit|offset|fetch|with|for|option|set)\\b";

    /** lookahead로 막을 힌트 키워드 — Hint enum에서 동적으로 생성되어 새 값 추가 시 자동 반영된다. */
    private static final String HINT_TOKENS = Hint.regexAlternation();

    /** 사용자가 직접 박은 힌트가 SQL 어딘가에 있으면 변환 전체를 스킵하기 위한 검사 패턴. */
    private static final Pattern HINT_PRESENT = Pattern.compile(
            "\\bwith\\s*\\(\\s*" + HINT_TOKENS + "\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * FROM 또는 JOIN 뒤의 테이블 식별자를 매칭. 단일 패스로 둘 다 처리해 hot path 비용을 절반으로 줄인다.
     * <p>정규식 그룹 구조:
     * <ul>
     *   <li>group(1): "from " 또는 "join " + 공백</li>
     *   <li>group(2): 테이블 식별자 (스키마.테이블 또는 [테이블] 가능)</li>
     *   <li>group(3): alias 부분 (있을 때) — 비어있을 수 있음</li>
     * </ul>
     */
    private static final Pattern FROM_OR_JOIN_TABLE = Pattern.compile(
            "(\\b(?:from|join)\\s+)(\\[?\\w+]?(?:\\.\\[?\\w+]?)?)((?:\\s+(?:as\\s+)?(?!" + RESERVED + ")\\w+)?)(?!\\s*with\\s*\\(\\s*" + HINT_TOKENS + ")",
            Pattern.CASE_INSENSITIVE
    );

    private final Mode mode;
    private final TablePatternMatcher excludeTables;
    private final TablePatternMatcher alwaysApplyTables;
    private final boolean requireReadOnly;
    private final boolean logTransformedSql;
    private final int logTransformedSqlMaxLength;
    private final int maxSqlLength;

    /** Hibernate가 SPI로 직접 인스턴스화할 때 쓰는 기본 생성자. 일반적으로는 {@link #builder()} 사용. */
    public NoLockStatementInspector() {
        this(builder());
    }

    private NoLockStatementInspector(Builder b) {
        this.mode = b.mode;
        warnOnSchemaQualified(b.excludeTables, "exclude-tables");
        warnOnSchemaQualified(b.alwaysApplyTables, "always-apply-tables");
        this.excludeTables = new TablePatternMatcher(b.excludeTables);
        this.alwaysApplyTables = new TablePatternMatcher(b.alwaysApplyTables);
        this.requireReadOnly = b.requireReadOnly;
        this.logTransformedSql = b.logTransformedSql;
        this.logTransformedSqlMaxLength = b.logTransformedSqlMaxLength;
        this.maxSqlLength = b.maxSqlLength;
    }

    /**
     * exclude/alwaysApply 패턴에 {@code .} / {@code [} / {@code ]}가 포함되면 schema-qualified로
     * 입력했을 가능성이 높지만 실제 테이블 비교는 schema-less다. 운영자 오해를 방지하기 위해 WARN.
     */
    private static void warnOnSchemaQualified(List<String> patterns, String optionName) {
        if (patterns == null) return;
        for (String p : patterns) {
            if (p == null) continue;
            if (p.indexOf('.') >= 0 || p.indexOf('[') >= 0 || p.indexOf(']') >= 0) {
                log.warn("[jpa-mssql-hints] {} 패턴 '{}'에 schema/대괄호 문자가 포함되어 있습니다. " +
                        "테이블 비교는 schema-less라 의도와 다르게 매칭되지 않을 수 있습니다 " +
                        "(예: 'dbo.audit_log'은 'audit_log'와 매칭되지 않음).", optionName, p);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 빌더. 외부 사용처는 이걸 우선 사용. 기존 생성자들은 하위 호환을 위해 유지된다.
     */
    public static final class Builder {
        private Mode mode = Mode.ANNOTATION;
        private List<String> excludeTables = Collections.emptyList();
        private List<String> alwaysApplyTables = Collections.emptyList();
        private boolean requireReadOnly = false;
        private boolean logTransformedSql = false;
        private int logTransformedSqlMaxLength = 0;
        private int maxSqlLength = 0;

        private Builder() {
        }

        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        public Builder excludeTables(List<String> excludeTables) {
            this.excludeTables = excludeTables == null ? Collections.emptyList() : excludeTables;
            return this;
        }

        public Builder alwaysApplyTables(List<String> alwaysApplyTables) {
            this.alwaysApplyTables = alwaysApplyTables == null ? Collections.emptyList() : alwaysApplyTables;
            return this;
        }

        public Builder requireReadOnly(boolean requireReadOnly) {
            this.requireReadOnly = requireReadOnly;
            return this;
        }

        public Builder logTransformedSql(boolean logTransformedSql) {
            this.logTransformedSql = logTransformedSql;
            return this;
        }

        public Builder logTransformedSqlMaxLength(int max) {
            this.logTransformedSqlMaxLength = max;
            return this;
        }

        public Builder maxSqlLength(int max) {
            this.maxSqlLength = max;
            return this;
        }

        public NoLockStatementInspector build() {
            return new NoLockStatementInspector(this);
        }
    }

    @Override
    public String inspect(String sql) {
        // OFF 모드는 화이트리스트도 무시하는 절대 kill switch.
        if (sql == null || mode == Mode.OFF) {
            return sql;
        }
        // 매우 긴 SQL에 대한 ReDoS/CPU 폭주 방어. 0 이하면 무제한.
        if (maxSqlLength > 0 && sql.length() > maxSqlLength) {
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
        String result = appendHint(sql, FROM_OR_JOIN_TABLE, defaultActive, hintExpr);
        if (logTransformedSql && !result.equals(sql)) {
            SQL_LOG.debug("[jpa-mssql-hints] before: {}", truncateForLog(sql));
            SQL_LOG.debug("[jpa-mssql-hints] after : {}", truncateForLog(result));
        }
        return result;
    }

    /**
     * 운영 로그에 SQL 인라인 리터럴(이메일/토큰 등)이 흘러갈 위험을 줄이기 위한 truncate.
     * {@code logTransformedSqlMaxLength <= 0} 또는 길이 미만이면 원본 그대로.
     */
    private String truncateForLog(String s) {
        if (logTransformedSqlMaxLength <= 0 || s.length() <= logTransformedSqlMaxLength) {
            return s;
        }
        return s.substring(0, logTransformedSqlMaxLength) + "... (truncated)";
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
        return "WITH (" + hints.stream().map(Hint::sqlToken).collect(Collectors.joining(", ")) + ")";
    }

    private boolean alreadyHasHint(String sql) {
        return HINT_PRESENT.matcher(sql).find();
    }

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

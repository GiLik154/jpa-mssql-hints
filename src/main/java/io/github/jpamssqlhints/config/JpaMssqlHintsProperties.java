package io.github.jpamssqlhints.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * jpa-mssql-hints 라이브러리의 외부 설정. application.yml 또는
 * application.properties에서 {@code jpa-mssql-hints.*} 접두사로 바인딩된다.
 */
@ConfigurationProperties("jpa-mssql-hints")
public record JpaMssqlHintsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("ANNOTATION") Mode mode,
        @DefaultValue({}) List<String> excludeTables,
        @DefaultValue({}) List<String> alwaysApplyTables,
        /**
         * true면 readOnly 트랜잭션 안에서만 힌트가 적용된다.
         * <p><b>주의</b>: 트랜잭션이 없는 상태에서 발급되는 SELECT(예: OSIV off + Repository 직접 호출)도
         * 거부된다. 안전 측의 디폴트지만, "readOnly만 막는다"가 아니라 "readOnly가 명시되지 않은
         * 모든 SELECT를 막는다"는 점에 유의.
         */
        @DefaultValue("false") boolean requireReadOnly,
        @DefaultValue("false") boolean logTransformedSql,

        /**
         * SQL 로그 최대 길이. 0(기본) 이하면 무제한. 양수면 해당 길이로 잘라 {@code "... (truncated)"}을 부착.
         * 운영 로그에 SQL 인라인 리터럴(이메일/토큰 등)이 흘러갈 위험을 줄이기 위한 옵션.
         */
        @DefaultValue("0") int logTransformedSqlMaxLength,

        /**
         * 변환 대상 SQL 최대 길이(byte 아닌 char). 0(기본) 이하면 무제한.
         * 양수면 해당 길이를 초과하는 SQL은 정규식 매칭 없이 그대로 반환 — 매우 긴 native query에 대한 ReDoS 방어.
         */
        @DefaultValue("0") int maxSqlLength
) {
}

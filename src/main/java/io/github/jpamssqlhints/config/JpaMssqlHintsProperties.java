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
        @DefaultValue({}) List<String> alwaysApplyTables
) {
}

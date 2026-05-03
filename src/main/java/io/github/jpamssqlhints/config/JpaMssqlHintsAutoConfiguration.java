package io.github.jpamssqlhints.config;

import io.github.jpamssqlhints.aspect.TableHintAspect;
import io.github.jpamssqlhints.inspector.NoLockStatementInspector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@ConditionalOnClass(name = {
        "org.hibernate.resource.jdbc.spi.StatementInspector",
        "org.aspectj.lang.ProceedingJoinPoint"
})
@ConditionalOnProperty(prefix = "jpa-mssql-hints", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(JpaMssqlHintsProperties.class)
@EnableAspectJAutoProxy
public class JpaMssqlHintsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NoLockStatementInspector noLockStatementInspector(JpaMssqlHintsProperties properties) {
        return NoLockStatementInspector.builder()
                                       .mode(properties.mode())
                                       .excludeTables(properties.excludeTables())
                                       .alwaysApplyTables(properties.alwaysApplyTables())
                                       .requireReadOnly(properties.requireReadOnly())
                                       .logTransformedSql(properties.logTransformedSql())
                                       .logTransformedSqlMaxLength(properties.logTransformedSqlMaxLength())
                                       .maxSqlLength(properties.maxSqlLength())
                                       .build();
    }

    @Bean
    public HibernatePropertiesCustomizer noLockHibernateCustomizer(NoLockStatementInspector inspector) {
        return props -> props.put("hibernate.session_factory.statement_inspector", inspector);
    }

    @Bean
    @ConditionalOnMissingBean
    public TableHintAspect tableHintAspect() {
        return new TableHintAspect();
    }
}

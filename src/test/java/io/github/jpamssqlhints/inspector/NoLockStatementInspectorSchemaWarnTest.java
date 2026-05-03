package io.github.jpamssqlhints.inspector;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("schema-qualified 패턴 사용 시 시작 단계 WARN")
class NoLockStatementInspectorSchemaWarnTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger inspectorLogger;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        inspectorLogger = (Logger) LoggerFactory.getLogger(NoLockStatementInspector.class);
        originalLevel = inspectorLogger.getLevel();
        inspectorLogger.setLevel(Level.WARN);
        appender = new ListAppender<>();
        appender.start();
        inspectorLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        inspectorLogger.detachAppender(appender);
        inspectorLogger.setLevel(originalLevel);
        appender.stop();
    }

    @Test
    @DisplayName("excludeTables에 'dbo.audit_log' 적으면 WARN")
    void schema_점이_포함된_excludeTables_WARN() {
        NoLockStatementInspector.builder()
                                .excludeTables(List.of("dbo.audit_log"))
                                .build();

        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("dbo.audit_log")
                        && e.getFormattedMessage().contains("exclude-tables"));
    }

    @Test
    @DisplayName("alwaysApplyTables에 '[member]' 적으면 WARN")
    void 대괄호가_포함된_alwaysApplyTables_WARN() {
        NoLockStatementInspector.builder()
                                .alwaysApplyTables(List.of("[member]"))
                                .build();

        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("[member]")
                        && e.getFormattedMessage().contains("always-apply-tables"));
    }

    @Test
    @DisplayName("정상 패턴(스키마/대괄호 없음)은 WARN 없음")
    void 정상_패턴은_WARN_없음() {
        NoLockStatementInspector.builder()
                                .excludeTables(List.of("audit_log", "payment_*"))
                                .alwaysApplyTables(List.of("stat_*"))
                                .build();

        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("excludeTables/alwaysApplyTables를 null로 빌드해도 WARN 없이 정상")
    void null_리스트는_WARN_없음() {
        NoLockStatementInspector.builder()
                                .excludeTables(null)
                                .alwaysApplyTables(null)
                                .build();

        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("리스트 안에 null 항목이 섞여도 WARN 없음 (null은 건너뜀)")
    void null_항목은_건너뜀() {
        NoLockStatementInspector.builder()
                                .excludeTables(java.util.Arrays.asList(null, "audit_log"))
                                .build();

        assertThat(appender.list).isEmpty();
    }
}

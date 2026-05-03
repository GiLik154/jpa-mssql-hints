package io.github.jpamssqlhints.inspector;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.jpamssqlhints.config.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockStatementInspectorLoggingTest {

    private static final String SQL_LOGGER_NAME = "io.github.jpamssqlhints.SQL";

    private ListAppender<ILoggingEvent> appender;
    private Logger sqlLogger;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        sqlLogger = (Logger) LoggerFactory.getLogger(SQL_LOGGER_NAME);
        originalLevel = sqlLogger.getLevel();
        sqlLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        sqlLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        sqlLogger.detachAppender(appender);
        sqlLogger.setLevel(originalLevel);
        appender.stop();
    }

    @Test
    @DisplayName("logTransformedSql=true이고 SQL이 변환되면 로그 출력")
    void 변환되면_로그_출력() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .logTransformedSql(true)
                                                                     .build();
        inspector.inspect("select * from member where id = 1");

        assertThat(appender.list)
                .as("변환 후 SQL이 로그에 포함되어야 함")
                .anyMatch(event -> event.getFormattedMessage().toLowerCase().contains("with (nolock)"));
    }

    @Test
    @DisplayName("SELECT지만 모든 테이블이 블랙리스트라 결과가 원본과 동일하면 로그 없음")
    void 결과가_원본과_같으면_로그_없음() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .excludeTables(List.of("member"))
                                                                     .logTransformedSql(true)
                                                                     .build();
        inspector.inspect("select * from member where id = 1");

        assertThat(appender.list)
                .as("매칭된 테이블이 모두 블랙되어 변환 결과가 원본과 동일 → 로그 안 남음")
                .isEmpty();
    }

    @Test
    @DisplayName("logTransformedSql=true여도 변환이 일어나지 않으면 로그 없음")
    void 변환_없으면_로그_없음() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .logTransformedSql(true)
                                                                     .build();
        inspector.inspect("insert into member values (1, 'a')");

        assertThat(appender.list)
                .as("DML은 변환 대상 아님")
                .isEmpty();
    }

    @Test
    @DisplayName("logTransformedSql=false이면 변환되어도 로그 없음")
    void 옵션_꺼지면_로그_없음() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .logTransformedSql(false)
                                                                     .build();
        inspector.inspect("select * from member where id = 1");

        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("logTransformedSqlMaxLength가 양수면 SQL 로그가 잘리고 truncated 표시 부착")
    void 길이_제한이_있으면_truncate() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .logTransformedSql(true)
                                                                     .logTransformedSqlMaxLength(20)
                                                                     .build();
        inspector.inspect("select * from member where email = 'secret@example.com'");

        assertThat(appender.list)
                .as("로그 메시지에 잘린 표시가 있어야 함")
                .anyMatch(event -> event.getFormattedMessage().contains("(truncated)"));
        assertThat(appender.list)
                .as("원본 민감 리터럴이 로그에 그대로 남아있으면 안 됨")
                .noneMatch(event -> event.getFormattedMessage().contains("secret@example.com"));
    }

    @Test
    @DisplayName("logTransformedSqlMaxLength=양수지만 SQL이 그 길이 이내면 자르지 않음")
    void 길이_제한_안에_들어오면_그대로() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .logTransformedSql(true)
                                                                     .logTransformedSqlMaxLength(1000)
                                                                     .build();
        inspector.inspect("select * from member");

        assertThat(appender.list)
                .as("길이가 1000자 이내라 truncate 표시 없어야 함")
                .noneMatch(event -> event.getFormattedMessage().contains("(truncated)"));
    }

    @Test
    @DisplayName("logTransformedSqlMaxLength=0(기본)이면 자르지 않음")
    void 길이_제한_0이면_무제한() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .logTransformedSql(true)
                                                                     .logTransformedSqlMaxLength(0)
                                                                     .build();
        inspector.inspect("select * from member where email = 'secret@example.com'");

        assertThat(appender.list)
                .as("기본값이면 원본 SQL이 그대로 출력")
                .anyMatch(event -> event.getFormattedMessage().contains("secret@example.com"));
        assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains("(truncated)"));
    }
}

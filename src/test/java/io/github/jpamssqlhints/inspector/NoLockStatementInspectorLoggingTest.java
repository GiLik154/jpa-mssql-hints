package io.github.jpamssqlhints.inspector;

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

    private ListAppender<ILoggingEvent> appender;
    private Logger inspectorLogger;

    @BeforeEach
    void attachAppender() {
        inspectorLogger = (Logger) LoggerFactory.getLogger(NoLockStatementInspector.class);
        appender = new ListAppender<>();
        appender.start();
        inspectorLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        inspectorLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("logTransformedSql=true이고 SQL이 변환되면 로그 출력")
    void 변환되면_로그_출력() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of(), List.of(), false, true
        );
        inspector.inspect("select * from member where id = 1");

        assertThat(appender.list)
                .as("변환 후 SQL이 로그에 포함되어야 함")
                .anyMatch(event -> event.getFormattedMessage().toLowerCase().contains("with (nolock)"));
    }

    @Test
    @DisplayName("logTransformedSql=true여도 변환이 일어나지 않으면 로그 없음")
    void 변환_없으면_로그_없음() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of(), List.of(), false, true
        );
        inspector.inspect("insert into member values (1, 'a')");

        assertThat(appender.list)
                .as("DML은 변환 대상 아님")
                .isEmpty();
    }

    @Test
    @DisplayName("logTransformedSql=false이면 변환되어도 로그 없음")
    void 옵션_꺼지면_로그_없음() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of(), List.of(), false, false
        );
        inspector.inspect("select * from member where id = 1");

        assertThat(appender.list).isEmpty();
    }
}

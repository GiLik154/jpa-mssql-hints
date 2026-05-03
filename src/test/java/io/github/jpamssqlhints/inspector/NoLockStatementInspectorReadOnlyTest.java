package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.config.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockStatementInspectorReadOnlyTest {

    private static final String SELECT_SQL = "select * from member where id = 1";

    @BeforeEach
    void initTx() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTx() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.clear();
    }

    @Test
    @DisplayName("requireReadOnly=true + readOnly 트랜잭션 → NOLOCK 적용")
    void readOnly_트랜잭션_적용() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.GLOBAL)
                .requireReadOnly(true)
                .build();
        assertThat(inspector.inspect(SELECT_SQL)).containsIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("requireReadOnly=true + 쓰기 트랜잭션 → NOLOCK 미적용 (안전장치)")
    void 쓰기_트랜잭션_미적용() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.GLOBAL)
                .requireReadOnly(true)
                .build();
        assertThat(inspector.inspect(SELECT_SQL)).isEqualTo(SELECT_SQL);
    }

    @Test
    @DisplayName("requireReadOnly=true + 트랜잭션 없음 → 미적용")
    void 트랜잭션_없음_미적용() {
        // setActualTransactionActive 호출 안 함
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.GLOBAL)
                .requireReadOnly(true)
                .build();
        assertThat(inspector.inspect(SELECT_SQL)).isEqualTo(SELECT_SQL);
    }

    @Test
    @DisplayName("requireReadOnly=false (기본) + 쓰기 트랜잭션 → 정상 적용")
    void 안전장치_미사용시_정상() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.GLOBAL)
                .requireReadOnly(false)
                .build();
        assertThat(inspector.inspect(SELECT_SQL)).containsIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("requireReadOnly=true이면 화이트리스트도 readOnly가 아니면 미적용")
    void 화이트리스트도_안전장치_적용() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.ANNOTATION)
                .alwaysApplyTables(List.of("member"))
                .requireReadOnly(true)
                .build();
        assertThat(inspector.inspect(SELECT_SQL)).isEqualTo(SELECT_SQL);
    }
}

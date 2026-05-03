package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.config.Mode;
import io.github.jpamssqlhints.context.HintContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockStatementInspectorGlobTest {

    @AfterEach
    void cleanup() {
        while (HintContext.isActive()) {
            HintContext.exit();
        }
    }

    @Nested
    @DisplayName("화이트리스트에 glob 패턴")
    class 화이트_glob {

        @ParameterizedTest(name = "[{index}] stat_* → {0}")
        @ValueSource(strings = {"stat_daily", "stat_monthly", "stat_user_summary"})
        @DisplayName("prefix 와일드카드 — stat_*")
        void prefix(String table) {
            NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                    .mode(Mode.ANNOTATION)
                    .alwaysApplyTables(List.of("stat_*"))
                    .build();
            String sql = "select * from " + table + " where dt = '2026-05-03'";
            assertThat(inspector.inspect(sql)).containsIgnoringCase("WITH (NOLOCK)");
        }

        @ParameterizedTest(name = "[{index}] *_log → {0}")
        @ValueSource(strings = {"access_log", "audit_log", "request_log"})
        @DisplayName("suffix 와일드카드 — *_log")
        void suffix(String table) {
            NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                    .mode(Mode.ANNOTATION)
                    .alwaysApplyTables(List.of("*_log"))
                    .build();
            String sql = "select * from " + table + " where ts > '2026-05-01'";
            assertThat(inspector.inspect(sql)).containsIgnoringCase("WITH (NOLOCK)");
        }

        @Test
        @DisplayName("중간 와일드카드 — *audit*")
        void 중간() {
            NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                    .mode(Mode.ANNOTATION)
                    .alwaysApplyTables(List.of("*audit*"))
                    .build();
            String result = inspector.inspect("select * from user_audit_log where id = 1");
            assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
        }

        @Test
        @DisplayName("패턴에 매칭되지 않으면 적용 안 됨")
        void 미매칭() {
            NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                    .mode(Mode.ANNOTATION)
                    .alwaysApplyTables(List.of("stat_*"))
                    .build();
            String sql = "select * from member where id = 1";
            assertThat(inspector.inspect(sql)).isEqualTo(sql);
        }
    }

    @Nested
    @DisplayName("블랙리스트에 glob 패턴")
    class 블랙_glob {

        @ParameterizedTest(name = "[{index}] payment_* → {0}")
        @ValueSource(strings = {"payment_main", "payment_history", "payment_pending"})
        @DisplayName("prefix 와일드카드는 블랙리스트로 일괄 차단")
        void prefix_차단(String table) {
            NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                    .mode(Mode.GLOBAL)
                    .excludeTables(List.of("payment_*"))
                    .build();
            String sql = "select * from " + table + " where id = 1";
            assertThat(inspector.inspect(sql)).doesNotContainIgnoringCase("WITH (NOLOCK)");
        }

        @Test
        @DisplayName("패턴 외 테이블은 정상 적용")
        void 패턴_외_적용() {
            NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                    .mode(Mode.GLOBAL)
                    .excludeTables(List.of("payment_*"))
                    .build();
            String result = inspector.inspect("select * from member where id = 1");
            assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
        }
    }

    @Nested
    @DisplayName("정확 매칭은 회귀 없이 동작")
    class 정확_매칭_회귀 {

        @Test
        @DisplayName("점이 들어간 SQL 식별자도 escape되어 정확히 매칭")
        void 정확_매칭_그대로() {
            NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                    .mode(Mode.GLOBAL)
                    .excludeTables(List.of("payment"))
                    .build();
            // "payment"는 "payments"와 다른 테이블 — 정확 매칭이므로 NOLOCK 박혀야 함
            String result = inspector.inspect("select * from payments where id = 1");
            assertThat(result)
                    .as("payment 패턴이 payments에 매칭되면 안 됨")
                    .containsIgnoringCase("WITH (NOLOCK)");
        }
    }
}

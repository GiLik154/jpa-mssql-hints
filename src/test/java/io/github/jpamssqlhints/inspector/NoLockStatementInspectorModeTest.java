package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.annotation.Hint;
import io.github.jpamssqlhints.config.Mode;
import io.github.jpamssqlhints.context.HintContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockStatementInspectorModeTest {

    private static final String SELECT_SQL = "select * from member where id = 1";

    @AfterEach
    void cleanup() {
        while (HintContext.isActive()) {
            HintContext.exit();
        }
    }

    @Nested
    @DisplayName("Mode.ANNOTATION (기본)")
    class ANNOTATION_모드 {

        private final NoLockStatementInspector inspector = NoLockStatementInspector.builder().mode(Mode.ANNOTATION).build();

        @Test
        @DisplayName("NoLockContext 비활성이면 변환 안 됨")
        void 비활성_미적용() {
            assertThat(inspector.inspect(SELECT_SQL)).isEqualTo(SELECT_SQL);
        }

        @Test
        @DisplayName("NoLockContext 활성이면 NOLOCK 적용")
        void 활성_적용() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            assertThat(inspector.inspect(SELECT_SQL)).containsIgnoringCase("WITH (NOLOCK)");
        }
    }

    @Nested
    @DisplayName("Mode.GLOBAL")
    class GLOBAL_모드 {

        private final NoLockStatementInspector inspector = NoLockStatementInspector.builder().mode(Mode.GLOBAL).build();

        @Test
        @DisplayName("NoLockContext 비활성이어도 모든 SELECT에 NOLOCK 적용")
        void 비활성도_적용() {
            assertThat(inspector.inspect(SELECT_SQL)).containsIgnoringCase("WITH (NOLOCK)");
        }

        @Test
        @DisplayName("INSERT/UPDATE는 GLOBAL이어도 변환 안 됨")
        void DML은_여전히_미적용() {
            String insert = "insert into member values (1, 'a')";
            String update = "update member set name = 'b' where id = 1";
            assertThat(inspector.inspect(insert)).isEqualTo(insert);
            assertThat(inspector.inspect(update)).isEqualTo(update);
        }
    }

    @Nested
    @DisplayName("Mode.OFF")
    class OFF_모드 {

        private final NoLockStatementInspector inspector = NoLockStatementInspector.builder().mode(Mode.OFF).build();

        @Test
        @DisplayName("NoLockContext 활성이어도 변환하지 않음 (kill switch)")
        void 활성이어도_미적용() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            assertThat(inspector.inspect(SELECT_SQL)).isEqualTo(SELECT_SQL);
        }

        @Test
        @DisplayName("비활성도 당연히 미적용")
        void 비활성_미적용() {
            assertThat(inspector.inspect(SELECT_SQL)).isEqualTo(SELECT_SQL);
        }
    }
}

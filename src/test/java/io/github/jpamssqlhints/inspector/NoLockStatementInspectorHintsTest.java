package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.annotation.Hint;
import io.github.jpamssqlhints.config.Mode;
import io.github.jpamssqlhints.context.HintContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockStatementInspectorHintsTest {

    private final NoLockStatementInspector inspector = NoLockStatementInspector.builder().mode(Mode.ANNOTATION).build();

    @AfterEach
    void cleanup() {
        while (HintContext.isActive()) {
            HintContext.exit();
        }
    }

    @Test
    @DisplayName("HintContext에 NOLOCK만 있으면 WITH (NOLOCK) 적용")
    void NOLOCK_단일() {
        HintContext.enter(Set.of(Hint.NOLOCK));
        String result = inspector.inspect("select * from member where id = 1");
        assertThat(result).contains("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("HintContext에 READPAST만 있으면 WITH (READPAST) 적용")
    void READPAST_단일() {
        HintContext.enter(Set.of(Hint.READPAST));
        String result = inspector.inspect("select * from member where id = 1");
        assertThat(result).contains("WITH (READPAST)");
        assertThat(result).doesNotContain("NOLOCK");
    }

    @Test
    @DisplayName("HintContext에 UPDLOCK만 있으면 WITH (UPDLOCK) 적용")
    void UPDLOCK_단일() {
        HintContext.enter(Set.of(Hint.UPDLOCK));
        String result = inspector.inspect("select * from member where id = 1");
        assertThat(result).contains("WITH (UPDLOCK)");
    }

    @Test
    @DisplayName("복수 힌트는 enum 선언 순서대로 결합 — UPDLOCK + ROWLOCK")
    void 복수_UPDLOCK_ROWLOCK() {
        HintContext.enter(Set.of(Hint.UPDLOCK, Hint.ROWLOCK));
        String result = inspector.inspect("select * from member where id = 1");
        assertThat(result).contains("WITH (UPDLOCK, ROWLOCK)");
    }

    @Test
    @DisplayName("복수 힌트 — NOLOCK + READPAST")
    void 복수_NOLOCK_READPAST() {
        HintContext.enter(Set.of(Hint.NOLOCK, Hint.READPAST));
        String result = inspector.inspect("select * from member where id = 1");
        assertThat(result).contains("WITH (NOLOCK, READPAST)");
    }

    @Test
    @DisplayName("GLOBAL 모드에서 컨텍스트가 비어있으면 기본 NOLOCK 적용 (하위 호환)")
    void GLOBAL_기본_NOLOCK() {
        NoLockStatementInspector global = NoLockStatementInspector.builder().mode(Mode.GLOBAL).build();
        // HintContext 비활성
        String result = global.inspect("select * from member where id = 1");
        assertThat(result).contains("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("화이트리스트도 컨텍스트 힌트 종류를 따름 (READPAST 컨텍스트면 READPAST 적용)")
    void 화이트리스트_컨텍스트_힌트_따름() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.ANNOTATION)
                                                                     .alwaysApplyTables(java.util.List.of("dashboard"))
                                                                     .build();
        HintContext.enter(Set.of(Hint.READPAST));
        String result = inspector.inspect("select * from dashboard where id = 1");
        assertThat(result).contains("WITH (READPAST)");
    }

}

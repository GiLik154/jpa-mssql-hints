package io.github.jpamssqlhints.context;

import io.github.jpamssqlhints.annotation.Hint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HintContext.open() — try-with-resources 안전 스코프")
class HintContextScopeTest {

    @AfterEach
    void cleanup() {
        while (HintContext.isActive()) {
            HintContext.exit();
        }
    }

    @Test
    @DisplayName("try-with-resources 블록 안에서 힌트 활성, 블록 종료 후 자동 비활성")
    void try_with_resources_사용() {
        try (HintContext.Scope s = HintContext.open(Set.of(Hint.NOLOCK))) {
            assertThat(HintContext.currentHints()).containsExactly(Hint.NOLOCK);
        }
        assertThat(HintContext.isActive()).as("스코프 종료 후 자동 정리").isFalse();
    }

    @Test
    @DisplayName("close()를 두 번 호출해도 스택이 망가지지 않음 (idempotent)")
    void close_두_번_호출해도_안전() {
        HintContext.Scope s = HintContext.open(Set.of(Hint.NOLOCK));
        s.close();
        s.close(); // 두 번째 close — 무시되어야 함
        assertThat(HintContext.isActive()).isFalse();
    }

    @Test
    @DisplayName("스코프 안에서 예외 발생해도 자동 정리")
    void 예외_시에도_자동_정리() {
        try (HintContext.Scope s = HintContext.open(Set.of(Hint.READPAST))) {
            throw new RuntimeException("boom");
        } catch (RuntimeException ignored) {
        }
        assertThat(HintContext.isActive()).isFalse();
    }

    @Test
    @DisplayName("중첩 스코프 — 안쪽 종료 후 바깥 힌트는 살아있음")
    void 중첩_스코프() {
        try (HintContext.Scope outer = HintContext.open(Set.of(Hint.NOLOCK))) {
            try (HintContext.Scope inner = HintContext.open(Set.of(Hint.READPAST))) {
                assertThat(HintContext.currentHints())
                        .as("바깥 + 안쪽 합집합")
                        .containsExactlyInAnyOrder(Hint.NOLOCK, Hint.READPAST);
            }
            assertThat(HintContext.currentHints())
                    .as("안쪽 종료 후 바깥만 남음")
                    .containsExactly(Hint.NOLOCK);
        }
        assertThat(HintContext.isActive()).isFalse();
    }
}

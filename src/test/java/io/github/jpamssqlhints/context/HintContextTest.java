package io.github.jpamssqlhints.context;

import io.github.jpamssqlhints.annotation.Hint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HintContext — ThreadLocal 스택 단위 테스트")
class HintContextTest {

    @AfterEach
    void cleanup() {
        while (HintContext.isActive()) {
            HintContext.exit();
        }
    }

    @Nested
    @DisplayName("초기 상태")
    class 초기_상태 {

        @Test
        @DisplayName("새 스레드에서 isActive=false, currentHints는 빈 Set")
        void 초기는_비활성() {
            assertThat(HintContext.isActive()).isFalse();
            assertThat(HintContext.currentHints()).isEmpty();
        }
    }

    @Nested
    @DisplayName("enter / exit")
    class enter_exit {

        @Test
        @DisplayName("enter 후 currentHints에 힌트가 들어있고 isActive=true")
        void enter_적용() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            assertThat(HintContext.isActive()).isTrue();
            assertThat(HintContext.currentHints()).containsExactly(Hint.NOLOCK);
        }

        @Test
        @DisplayName("exit 후 비활성으로 복귀")
        void exit_복귀() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            HintContext.exit();
            assertThat(HintContext.isActive()).isFalse();
            assertThat(HintContext.currentHints()).isEmpty();
        }

        @Test
        @DisplayName("null 또는 빈 Set으로 enter해도 안전 — frame은 push되지만 합집합은 비어있음")
        void null_또는_빈_Set_enter() {
            HintContext.enter(null);
            assertThat(HintContext.isActive())
                    .as("frame 자체는 push되어 active 상태")
                    .isTrue();
            assertThat(HintContext.currentHints()).isEmpty();
            HintContext.exit();

            HintContext.enter(Set.of());
            assertThat(HintContext.isActive()).isTrue();
            assertThat(HintContext.currentHints()).isEmpty();
        }

        @Test
        @DisplayName("초과 exit 호출은 무시되고 음수 stack을 만들지 않음")
        void 초과_exit_무시() {
            HintContext.exit();
            HintContext.exit();
            assertThat(HintContext.isActive()).isFalse();
            // 다시 enter도 정상 동작
            HintContext.enter(Set.of(Hint.NOLOCK));
            assertThat(HintContext.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("중첩 / 합집합")
    class 중첩 {

        @Test
        @DisplayName("중첩 enter 시 currentHints는 모든 frame의 합집합")
        void 합집합() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            HintContext.enter(Set.of(Hint.READPAST));

            assertThat(HintContext.currentHints())
                    .containsExactlyInAnyOrder(Hint.NOLOCK, Hint.READPAST);
        }

        @Test
        @DisplayName("안쪽 exit 후 바깥 frame은 살아있음")
        void 안쪽_exit_후_바깥_유지() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            HintContext.enter(Set.of(Hint.READPAST));
            HintContext.exit();

            assertThat(HintContext.isActive()).isTrue();
            assertThat(HintContext.currentHints()).containsExactly(Hint.NOLOCK);
        }

        @Test
        @DisplayName("같은 힌트가 여러 frame에 있어도 currentHints는 하나로 통합")
        void 중복_힌트_통합() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            HintContext.enter(Set.of(Hint.NOLOCK, Hint.READPAST));

            assertThat(HintContext.currentHints())
                    .containsExactlyInAnyOrder(Hint.NOLOCK, Hint.READPAST);
        }
    }

    @Nested
    @DisplayName("불변성 / ThreadLocal 격리")
    class 격리 {

        @Test
        @DisplayName("currentHints는 unmodifiable Set이라 외부에서 수정 불가")
        void unmodifiable_반환() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            Set<Hint> hints = HintContext.currentHints();
            assertThatUnsupported(() -> hints.add(Hint.READPAST));
        }

        @Test
        @DisplayName("새 스레드는 부모의 HintContext를 보지 못함")
        void 다른_스레드_격리() throws InterruptedException {
            HintContext.enter(Set.of(Hint.NOLOCK));
            AtomicReference<Set<Hint>> seenInOther = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);

            Thread t = new Thread(() -> {
                seenInOther.set(HintContext.currentHints());
                done.countDown();
            });
            t.start();
            done.await();

            assertThat(seenInOther.get())
                    .as("새 스레드는 ThreadLocal을 상속받지 않음")
                    .isEmpty();
            assertThat(HintContext.currentHints())
                    .as("원래 스레드는 여전히 NOLOCK 보유")
                    .containsExactly(Hint.NOLOCK);
        }

        @Test
        @DisplayName("스택이 비면 ThreadLocal에서 STACK 자체가 remove되어 누수 방지")
        void 스택_비면_remove() {
            HintContext.enter(Set.of(Hint.NOLOCK));
            HintContext.exit();
            // 다시 isActive 호출 — withInitial로 새 빈 stack을 받아오므로 false
            assertThat(HintContext.isActive()).isFalse();
            assertThat(HintContext.currentHints()).isEmpty();
        }
    }

    private static void assertThatUnsupported(Runnable r) {
        try {
            r.run();
            org.assertj.core.api.Assertions.fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}

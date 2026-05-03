package io.github.jpamssqlhints.aspect;

import io.github.jpamssqlhints.annotation.Hint;
import io.github.jpamssqlhints.annotation.TableHint;
import io.github.jpamssqlhints.context.HintContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TableHintAspectIntegrationTest.TestConfig.class)
class TableHintAspectIntegrationTest {

    @Autowired
    private SingleHintService single;

    @Autowired
    private MultiHintService multi;

    @Autowired
    private ClassLevelHintService classLevel;

    @Test
    void NOLOCK_단일() {
        assertThat(single.callNoLock()).isEqualTo(Set.of(Hint.NOLOCK));
        assertThat(HintContext.isActive()).isFalse();
    }

    @Test
    void READPAST_단일() {
        assertThat(single.callReadPast()).isEqualTo(Set.of(Hint.READPAST));
    }

    @Test
    void UPDLOCK_ROWLOCK_복수() {
        assertThat(multi.callUpdLockRowLock())
                .containsExactlyInAnyOrder(Hint.UPDLOCK, Hint.ROWLOCK);
    }

    @Test
    void 클래스_레벨_TableHint() {
        assertThat(classLevel.callPlain()).isEqualTo(Set.of(Hint.READPAST));
    }

    @Test
    void 예외_시에도_HintContext_정리() {
        try {
            single.throwAfterEnter();
        } catch (RuntimeException ignored) {
        }
        assertThat(HintContext.isActive()).isFalse();
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        TableHintAspect tableHintAspect() {
            return new TableHintAspect();
        }

        @Bean
        SingleHintService single() {
            return new SingleHintService();
        }

        @Bean
        MultiHintService multi() {
            return new MultiHintService();
        }

        @Bean
        ClassLevelHintService classLevel() {
            return new ClassLevelHintService();
        }
    }

    @Component
    static class SingleHintService {
        @TableHint(Hint.NOLOCK)
        Set<Hint> callNoLock() {
            return HintContext.currentHints();
        }

        @TableHint(Hint.READPAST)
        Set<Hint> callReadPast() {
            return HintContext.currentHints();
        }

        @TableHint(Hint.NOLOCK)
        void throwAfterEnter() {
            throw new RuntimeException("boom");
        }
    }

    @Component
    static class MultiHintService {
        @TableHint({Hint.UPDLOCK, Hint.ROWLOCK})
        Set<Hint> callUpdLockRowLock() {
            return HintContext.currentHints();
        }
    }

    @Component
    @TableHint(Hint.READPAST)
    static class ClassLevelHintService {
        Set<Hint> callPlain() {
            return HintContext.currentHints();
        }
    }
}

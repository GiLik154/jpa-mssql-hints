package io.github.jpamssqlhints.aspect;

import io.github.jpamssqlhints.annotation.Hint;
import io.github.jpamssqlhints.annotation.NoLock;
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

@SpringBootTest(classes = TableHintAspectAbsorbsNoLockTest.TestConfig.class)
class TableHintAspectAbsorbsNoLockTest {

    @Autowired
    private NoLockOnlyService nlOnly;

    @Autowired
    private ClassLevelNoLockService classLevel;

    @Test
    void TableHintAspect_단독으로_메서드_NoLock_처리() {
        assertThat(nlOnly.callMethodNoLock()).isEqualTo(Set.of(Hint.NOLOCK));
        assertThat(HintContext.isActive()).isFalse();
    }

    @Test
    void TableHintAspect_단독으로_클래스_NoLock_처리() {
        assertThat(classLevel.callPlain()).isEqualTo(Set.of(Hint.NOLOCK));
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        // NoLockAspect 등록하지 않음 — TableHintAspect만으로 @NoLock이 동작해야 함
        @Bean
        TableHintAspect tableHintAspect() {
            return new TableHintAspect();
        }

        @Bean
        NoLockOnlyService nlOnly() {
            return new NoLockOnlyService();
        }

        @Bean
        ClassLevelNoLockService classLevel() {
            return new ClassLevelNoLockService();
        }
    }

    @Component
    static class NoLockOnlyService {
        @NoLock
        Set<Hint> callMethodNoLock() {
            return HintContext.currentHints();
        }
    }

    @Component
    @NoLock
    static class ClassLevelNoLockService {
        Set<Hint> callPlain() {
            return HintContext.currentHints();
        }
    }
}

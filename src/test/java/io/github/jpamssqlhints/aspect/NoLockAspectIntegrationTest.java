package io.github.jpamssqlhints.aspect;

import io.github.jpamssqlhints.annotation.NoLock;
import io.github.jpamssqlhints.context.NoLockContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NoLockAspectIntegrationTest.TestConfig.class)
class NoLockAspectIntegrationTest {

    @Autowired
    private OuterService outer;

    @Autowired
    private NestedService nested;

    @Autowired
    private NoMarkService plain;

    @Test
    void NoLock_메서드_안에서_컨텍스트가_활성() {
        Boolean active = outer.runAndCheck();
        assertThat(active).isTrue();
        assertThat(NoLockContext.isActive())
                .as("메서드 종료 후엔 정리되어야 함")
                .isFalse();
    }

    @Test
    void NoLock_없는_메서드는_비활성() {
        Boolean active = plain.runAndCheck();
        assertThat(active).isFalse();
    }

    @Test
    void 중첩_NoLock_호출에서도_바깥_구간이_유지됨() {
        Boolean innerStillActive = nested.outerThenInner();
        assertThat(innerStillActive)
                .as("inner 메서드 호출 후에도 outer 컨텍스트가 살아있어야 함")
                .isTrue();
        assertThat(NoLockContext.isActive()).isFalse();
    }

    @Test
    void 예외가_발생해도_컨텍스트가_정리됨() {
        try {
            outer.throwInside();
        } catch (RuntimeException ignored) {
        }
        assertThat(NoLockContext.isActive())
                .as("예외 시에도 finally로 exit 되어야 함")
                .isFalse();
    }

    @Test
    void 다른_스레드는_부모의_NoLock_컨텍스트를_보지_못함() throws InterruptedException {
        AtomicBoolean otherActive = new AtomicBoolean(true);
        CountDownLatch done = new CountDownLatch(1);

        outer.runWith(() -> {
            Thread t = new Thread(() -> {
                otherActive.set(NoLockContext.isActive());
                done.countDown();
            });
            t.start();
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(otherActive.get())
                .as("새 스레드는 ThreadLocal을 상속받지 않음 (트랜잭션과 동일한 한계)")
                .isFalse();
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        NoLockAspect noLockAspect() {
            return new NoLockAspect();
        }

        @Bean
        OuterService outerService() {
            return new OuterService(innerService());
        }

        @Bean
        InnerService innerService() {
            return new InnerService();
        }

        @Bean
        NestedService nestedService() {
            return new NestedService(innerService());
        }

        @Bean
        NoMarkService noMarkService() {
            return new NoMarkService();
        }
    }

    @Component
    static class OuterService {
        private final InnerService inner;

        OuterService(InnerService inner) {
            this.inner = inner;
        }

        @NoLock
        Boolean runAndCheck() {
            return NoLockContext.isActive();
        }

        @NoLock
        void throwInside() {
            throw new RuntimeException("boom");
        }

        @NoLock
        void runWith(Runnable r) {
            r.run();
        }
    }

    @Component
    static class InnerService {
        @NoLock
        Boolean checkActive() {
            return NoLockContext.isActive();
        }
    }

    @Component
    static class NestedService {
        private final InnerService inner;

        NestedService(InnerService inner) {
            this.inner = inner;
        }

        @NoLock
        Boolean outerThenInner() {
            inner.checkActive();
            return NoLockContext.isActive();
        }
    }

    @Component
    static class NoMarkService {
        Boolean runAndCheck() {
            return NoLockContext.isActive();
        }
    }
}

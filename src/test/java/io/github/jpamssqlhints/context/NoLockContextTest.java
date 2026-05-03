package io.github.jpamssqlhints.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockContextTest {

    @AfterEach
    void cleanup() {
        while (NoLockContext.isActive()) {
            NoLockContext.exit();
        }
    }

    @Test
    void 초기상태는_비활성() {
        assertThat(NoLockContext.isActive()).isFalse();
    }

    @Test
    void enter_후_활성() {
        NoLockContext.enter();
        assertThat(NoLockContext.isActive()).isTrue();
        NoLockContext.exit();
        assertThat(NoLockContext.isActive()).isFalse();
    }

    @Test
    void 중첩_enter_시_안쪽_exit에서도_바깥은_여전히_활성() {
        NoLockContext.enter();          // depth = 1
        NoLockContext.enter();          // depth = 2
        assertThat(NoLockContext.isActive()).isTrue();

        NoLockContext.exit();           // depth = 1
        assertThat(NoLockContext.isActive())
                .as("바깥 NoLock 구간이 아직 살아있어야 함")
                .isTrue();

        NoLockContext.exit();           // depth = 0
        assertThat(NoLockContext.isActive()).isFalse();
    }

    @Test
    void 깊이_초과_exit는_무시되고_음수가_되지_않음() {
        NoLockContext.exit();
        NoLockContext.exit();
        assertThat(NoLockContext.isActive()).isFalse();

        NoLockContext.enter();
        assertThat(NoLockContext.isActive()).isTrue();
    }

    @Test
    void 다른_스레드로_상태가_누수되지_않음() throws InterruptedException {
        NoLockContext.enter();
        AtomicBoolean otherThreadActive = new AtomicBoolean(true);
        CountDownLatch done = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            otherThreadActive.set(NoLockContext.isActive());
            done.countDown();
        });
        t.start();
        done.await();

        assertThat(otherThreadActive.get())
                .as("새 스레드는 부모의 NoLock 컨텍스트를 보지 못해야 함")
                .isFalse();
        assertThat(NoLockContext.isActive())
                .as("원래 스레드는 여전히 활성")
                .isTrue();
    }
}

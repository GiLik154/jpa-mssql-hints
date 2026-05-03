package io.github.jpamssqlhints.context;

/**
 * 현재 스레드가 NoLock 활성 구간에 있는지를 추적합니다. 중첩된 @NoLock
 * 메서드 호출에서도 안전하도록 진입 횟수 카운터를 사용합니다.
 */
public final class NoLockContext {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private NoLockContext() {
    }

    public static void enter() {
        Integer current = DEPTH.get();
        DEPTH.set(current == null ? 1 : current + 1);
    }

    public static void exit() {
        Integer current = DEPTH.get();
        if (current == null) {
            return;
        }
        int next = current - 1;
        if (next <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(next);
        }
    }

    public static boolean isActive() {
        Integer current = DEPTH.get();
        return current != null && current > 0;
    }
}

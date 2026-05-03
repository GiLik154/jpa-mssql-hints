package io.github.jpamssqlhints.context;

import io.github.jpamssqlhints.annotation.Hint;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;

/**
 * 현재 스레드에서 활성인 테이블 힌트 집합을 스택으로 추적한다. 중첩된
 * 어노테이션 호출에서도 바깥 구간이 끝날 때까지 힌트가 유지되도록
 * 스택의 모든 프레임을 합집합으로 노출한다.
 */
public final class HintContext {

    private static final ThreadLocal<Deque<EnumSet<Hint>>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private HintContext() {
    }

    public static void enter(Set<Hint> hints) {
        EnumSet<Hint> snapshot = (hints == null || hints.isEmpty())
                ? EnumSet.noneOf(Hint.class)
                : EnumSet.copyOf(hints);
        STACK.get().push(snapshot);
    }

    public static void exit() {
        Deque<EnumSet<Hint>> stack = STACK.get();
        if (stack.isEmpty()) return;
        stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }

    public static Set<Hint> currentHints() {
        Deque<EnumSet<Hint>> stack = STACK.get();
        if (stack.isEmpty()) return Collections.emptySet();
        EnumSet<Hint> merged = EnumSet.noneOf(Hint.class);
        for (EnumSet<Hint> frame : stack) merged.addAll(frame);
        return Collections.unmodifiableSet(merged);
    }

    public static boolean isActive() {
        return !STACK.get().isEmpty();
    }
}

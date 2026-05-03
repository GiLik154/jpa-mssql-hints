package io.github.jpamssqlhints.context;

import io.github.jpamssqlhints.annotation.Hint;

import java.util.Set;

/**
 * 기존 {@link io.github.jpamssqlhints.annotation.NoLock} 어노테이션과의
 * 하위 호환을 위한 thin wrapper. 내부적으로 {@link HintContext}에
 * NOLOCK 힌트를 push/pop 한다.
 */
public final class NoLockContext {

    private NoLockContext() {
    }

    public static void enter() {
        HintContext.enter(Set.of(Hint.NOLOCK));
    }

    public static void exit() {
        HintContext.exit();
    }

    public static boolean isActive() {
        return HintContext.isActive();
    }
}

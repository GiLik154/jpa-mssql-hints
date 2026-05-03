package io.github.jpamssqlhints.aspect;

import io.github.jpamssqlhints.context.NoLockContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * {@link io.github.jpamssqlhints.annotation.NoLock} 메서드(또는 클래스)
 * 진입 시 {@link NoLockContext}에 깊이를 +1, 종료 시 -1. 트랜잭션 시작 전후로
 * 동작해야 하므로 가장 바깥쪽 우선순위로 설정합니다.
 */
@Aspect
@Order(0)
public class NoLockAspect {

    @Around("@annotation(io.github.jpamssqlhints.annotation.NoLock) "
            + "|| @within(io.github.jpamssqlhints.annotation.NoLock)")
    public Object applyNoLock(ProceedingJoinPoint pjp) throws Throwable {
        NoLockContext.enter();
        try {
            return pjp.proceed();
        } finally {
            NoLockContext.exit();
        }
    }
}

package io.github.jpamssqlhints.aspect;

import io.github.jpamssqlhints.annotation.Hint;
import io.github.jpamssqlhints.annotation.NoLock;
import io.github.jpamssqlhints.annotation.TableHint;
import io.github.jpamssqlhints.context.HintContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Aspect
@Order(TableHintAspect.ORDER)
public class TableHintAspect {

    /** 트랜잭션 시작 전후에 ThreadLocal을 push/pop해야 하므로 가장 바깥쪽 우선순위로 설정. */
    public static final int ORDER = 0;

    @Around("(@annotation(io.github.jpamssqlhints.annotation.TableHint) "
            + "|| @within(io.github.jpamssqlhints.annotation.TableHint) "
            + "|| @annotation(io.github.jpamssqlhints.annotation.NoLock) "
            + "|| @within(io.github.jpamssqlhints.annotation.NoLock)) "
            + "&& !execution(* java.lang.Object.*(..))")
    public Object applyTableHint(ProceedingJoinPoint pjp) throws Throwable {
        Set<Hint> hints = resolveHints(pjp);
        HintContext.enter(hints);
        try {
            return pjp.proceed();
        } finally {
            HintContext.exit();
        }
    }

    /**
     * 메서드 → 클래스 순으로 어노테이션을 찾아 힌트를 해석한다.
     * <ul>
     *   <li>메서드 어노테이션이 클래스 어노테이션을 덮어쓴다.</li>
     *   <li>메서드 빈 {@code @TableHint({})}는 클래스 단위 적용을 끄는 명시적 신호.</li>
     *   <li>{@code @NoLock}은 {@code @TableHint(NOLOCK)}의 별칭으로 처리.</li>
     * </ul>
     */
    private Set<Hint> resolveHints(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();

        // 1순위: 메서드 어노테이션
        TableHint methodTh = AnnotationUtils.findAnnotation(method, TableHint.class);
        NoLock methodNl = AnnotationUtils.findAnnotation(method, NoLock.class);
        if (methodTh != null || methodNl != null) {
            return combine(methodTh, methodNl);
        }

        // 2순위: 클래스 어노테이션
        Class<?> declaring = method.getDeclaringClass();
        TableHint classTh = AnnotationUtils.findAnnotation(declaring, TableHint.class);
        NoLock classNl = AnnotationUtils.findAnnotation(declaring, NoLock.class);
        return combine(classTh, classNl);
    }

    private Set<Hint> combine(TableHint th, NoLock nl) {
        EnumSet<Hint> set = EnumSet.noneOf(Hint.class);
        if (th != null && th.value().length > 0) {
            Collections.addAll(set, th.value());
        }
        if (nl != null) {
            set.add(Hint.NOLOCK);
        }
        return set;
    }
}

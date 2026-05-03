package io.github.jpamssqlhints.aspect;

import io.github.jpamssqlhints.annotation.Hint;
import io.github.jpamssqlhints.annotation.TableHint;
import io.github.jpamssqlhints.context.HintContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;

@Aspect
@Order(0)
public class TableHintAspect {

    @Around("@annotation(io.github.jpamssqlhints.annotation.TableHint) "
            + "|| @within(io.github.jpamssqlhints.annotation.TableHint)")
    public Object apply(ProceedingJoinPoint pjp) throws Throwable {
        Set<Hint> hints = resolveHints(pjp);
        HintContext.enter(hints);
        try {
            return pjp.proceed();
        } finally {
            HintContext.exit();
        }
    }

    private Set<Hint> resolveHints(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        TableHint annotation = AnnotationUtils.findAnnotation(method, TableHint.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(method.getDeclaringClass(), TableHint.class);
        }
        if (annotation == null || annotation.value().length == 0) {
            return EnumSet.noneOf(Hint.class);
        }
        return EnumSet.copyOf(Set.of(annotation.value()));
    }
}

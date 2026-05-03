package io.github.jpamssqlhints.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드/클래스 실행 구간 동안 발급되는 SELECT 쿼리에 지정된 테이블 힌트(들)를
 * 자동 주입한다. 복수 지정 시 SQL에는 enum 선언 순서대로
 * {@code WITH (HINT1, HINT2)} 형태로 결합된다.
 *
 * <pre>{@code
 * @TableHint(Hint.READPAST)                  // 락 걸린 행은 스킵
 * @TableHint({Hint.UPDLOCK, Hint.ROWLOCK})   // 행 단위 update 락
 * }</pre>
 *
 * <p>{@code @NoLock}은 {@code @TableHint(Hint.NOLOCK)}의 별칭으로 유지된다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TableHint {
    Hint[] value();
}

package io.github.jpamssqlhints.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드/클래스 실행 구간 동안 발급되는 SELECT 쿼리에 MS-SQL의
 * {@code WITH (NOLOCK)} 테이블 힌트를 자동 주입합니다.
 *
 * <p>주의: NOLOCK은 사실상 READ UNCOMMITTED와 같아 dirty/phantom read를
 * 허용합니다. 정합성이 중요한 경로에는 사용하지 말고, 가능하면 RCSI
 * (READ_COMMITTED_SNAPSHOT ON)를 먼저 검토하세요.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoLock {
}

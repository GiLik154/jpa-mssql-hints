package io.github.jpamssqlhints.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("어노테이션 메타: @Inherited, @Documented")
class AnnotationMetaTest {

    @Test
    @DisplayName("@TableHint은 @Inherited다 — 부모 클래스 어노테이션이 자식에 상속")
    void TableHint은_Inherited() {
        assertThat(TableHint.class.isAnnotationPresent(Inherited.class)).isTrue();
    }

    @Test
    @DisplayName("@TableHint은 @Documented다")
    void TableHint은_Documented() {
        assertThat(TableHint.class.isAnnotationPresent(Documented.class)).isTrue();
    }

    @Test
    @DisplayName("@NoLock은 @Inherited다")
    void NoLock은_Inherited() {
        assertThat(NoLock.class.isAnnotationPresent(Inherited.class)).isTrue();
    }

    @Test
    @DisplayName("@NoLock은 @Documented다")
    void NoLock은_Documented() {
        assertThat(NoLock.class.isAnnotationPresent(Documented.class)).isTrue();
    }

    @Test
    @DisplayName("부모 클래스의 @TableHint가 자식 클래스에 상속된다")
    void 부모_TableHint가_자식에_상속() {
        assertThat(Child.class.isAnnotationPresent(TableHint.class)).isTrue();
        TableHint th = Child.class.getAnnotation(TableHint.class);
        assertThat(th.value()).containsExactly(Hint.READPAST);
    }

    @Test
    @DisplayName("부모 클래스의 @NoLock가 자식 클래스에 상속된다")
    void 부모_NoLock가_자식에_상속() {
        assertThat(NoLockChild.class.isAnnotationPresent(NoLock.class)).isTrue();
    }

    @TableHint(Hint.READPAST)
    static class Parent {
    }

    static class Child extends Parent {
    }

    @NoLock
    static class NoLockParent {
    }

    static class NoLockChild extends NoLockParent {
    }
}

package io.github.jpamssqlhints.config;

import io.github.jpamssqlhints.aspect.TableHintAspect;
import io.github.jpamssqlhints.inspector.NoLockStatementInspector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JpaMssqlHintsAutoConfiguration — 빈 등록 단위 테스트")
class JpaMssqlHintsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JpaMssqlHintsAutoConfiguration.class));

    @Nested
    @DisplayName("opt-in (enabled=true)")
    class opt_in {

        @Test
        @DisplayName("enabled=true면 NoLockStatementInspector / TableHintAspect / HibernatePropertiesCustomizer 모두 등록")
        void 모든_빈_등록() {
            runner
                    .withPropertyValues("jpa-mssql-hints.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(NoLockStatementInspector.class);
                        assertThat(context).hasSingleBean(TableHintAspect.class);
                        assertThat(context).hasSingleBean(HibernatePropertiesCustomizer.class);
                    });
        }

        @Test
        @DisplayName("Properties의 옵션이 Inspector 빌더에 전달된다")
        void 옵션_전달() {
            runner
                    .withPropertyValues(
                            "jpa-mssql-hints.enabled=true",
                            "jpa-mssql-hints.mode=GLOBAL",
                            "jpa-mssql-hints.exclude-tables=payment,audit_log",
                            "jpa-mssql-hints.always-apply-tables=stat_*"
                    )
                    .run(context -> {
                        assertThat(context).hasSingleBean(NoLockStatementInspector.class);
                        NoLockStatementInspector inspector = context.getBean(NoLockStatementInspector.class);
                        // GLOBAL 모드 + payment 블랙리스트 → payment에는 NOLOCK이 박히지 않아야 함
                        assertThat(inspector.inspect("select * from payment where id = 1"))
                                .doesNotContainIgnoringCase("WITH (NOLOCK)");
                        // GLOBAL 모드 → 일반 테이블에는 박힘
                        assertThat(inspector.inspect("select * from member"))
                                .containsIgnoringCase("WITH (NOLOCK)");
                    });
        }
    }

    @Nested
    @DisplayName("opt-out (기본 / enabled=false)")
    class opt_out {

        @Test
        @DisplayName("enabled 명시 안 하면 어떤 빈도 등록되지 않음 (opt-in 정책)")
        void 기본은_비활성() {
            runner.run(context -> {
                assertThat(context).doesNotHaveBean(NoLockStatementInspector.class);
                assertThat(context).doesNotHaveBean(TableHintAspect.class);
                assertThat(context).doesNotHaveBean(JpaMssqlHintsProperties.class);
            });
        }

        @Test
        @DisplayName("enabled=false면 비활성")
        void false_명시() {
            runner
                    .withPropertyValues("jpa-mssql-hints.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(NoLockStatementInspector.class);
                        assertThat(context).doesNotHaveBean(TableHintAspect.class);
                    });
        }
    }

    @Nested
    @DisplayName("@ConditionalOnMissingBean")
    class missing_bean {

        @Test
        @DisplayName("사용자가 직접 NoLockStatementInspector 빈을 등록하면 AutoConfiguration의 빈은 등록 안 됨")
        void 사용자_빈_우선() {
            runner
                    .withPropertyValues("jpa-mssql-hints.enabled=true")
                    .withBean("noLockStatementInspector", NoLockStatementInspector.class,
                            () -> NoLockStatementInspector.builder().mode(Mode.OFF).build())
                    .run(context -> {
                        assertThat(context).hasSingleBean(NoLockStatementInspector.class);
                        // 사용자 빈은 OFF 모드 → 변환 안 함
                        NoLockStatementInspector inspector = context.getBean(NoLockStatementInspector.class);
                        assertThat(inspector.inspect("select * from member"))
                                .doesNotContainIgnoringCase("WITH (NOLOCK)");
                    });
        }

        @Test
        @DisplayName("사용자가 직접 TableHintAspect 빈을 등록하면 AutoConfiguration의 Aspect는 등록 안 됨")
        void 사용자_Aspect_우선() {
            TableHintAspect userAspect = new TableHintAspect();
            runner
                    .withPropertyValues("jpa-mssql-hints.enabled=true")
                    .withBean("tableHintAspect", TableHintAspect.class, () -> userAspect)
                    .run(context -> {
                        assertThat(context).hasSingleBean(TableHintAspect.class);
                        assertThat(context.getBean(TableHintAspect.class)).isSameAs(userAspect);
                    });
        }
    }
}

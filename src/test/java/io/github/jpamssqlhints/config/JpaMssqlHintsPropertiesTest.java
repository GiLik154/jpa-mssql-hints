package io.github.jpamssqlhints.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JpaMssqlHintsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JpaMssqlHintsAutoConfiguration.class));

    @Test
    @DisplayName("enabled=true 명시 시 다른 옵션은 기본값으로 바인딩된다")
    void 기본값_바인딩() {
        contextRunner
                .withPropertyValues("jpa-mssql-hints.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JpaMssqlHintsProperties.class);
                    JpaMssqlHintsProperties props = context.getBean(JpaMssqlHintsProperties.class);
                    assertThat(props.enabled()).isTrue();
                    assertThat(props.mode()).isEqualTo(Mode.ANNOTATION);
                });
    }

    @Test
    @DisplayName("jpa-mssql-hints.enabled=false면 AutoConfiguration 비활성")
    void enabled_false_비활성() {
        contextRunner
                .withPropertyValues("jpa-mssql-hints.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JpaMssqlHintsProperties.class);
                });
    }

    @Test
    @DisplayName("opt-in: enabled를 명시하지 않으면 AutoConfiguration 비활성")
    void enabled_명시_안하면_비활성() {
        contextRunner.run(context -> {
            assertThat(context)
                    .as("스타터를 의도치 않게 추가한 사용자에게 자동 활성화되지 않도록 opt-in")
                    .doesNotHaveBean(JpaMssqlHintsProperties.class);
        });
    }

    @Test
    @DisplayName("enabled=true를 명시하면 빈이 등록된다")
    void enabled_true_명시하면_등록() {
        contextRunner
                .withPropertyValues("jpa-mssql-hints.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JpaMssqlHintsProperties.class);
                    JpaMssqlHintsProperties props = context.getBean(JpaMssqlHintsProperties.class);
                    assertThat(props.enabled()).isTrue();
                });
    }
}

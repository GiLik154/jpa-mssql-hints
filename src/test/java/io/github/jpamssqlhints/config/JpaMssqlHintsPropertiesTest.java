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
    @DisplayName("기본값으로 enabled=true가 바인딩되고 빈으로 등록된다")
    void 기본값_바인딩() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JpaMssqlHintsProperties.class);
            JpaMssqlHintsProperties props = context.getBean(JpaMssqlHintsProperties.class);
            assertThat(props.enabled()).isTrue();
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
}

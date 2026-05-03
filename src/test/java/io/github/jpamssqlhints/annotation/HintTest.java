package io.github.jpamssqlhints.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hint enum 정규식 alternation 동적 생성")
class HintTest {

    @Test
    @DisplayName("regexAlternation은 모든 enum 값을 소문자로 포함한다")
    void regexAlternation_모든_enum_값을_포함() {
        String alternation = Hint.regexAlternation();

        for (Hint h : Hint.values()) {
            assertThat(alternation)
                    .as("alternation은 %s를 포함해야 한다", h.name().toLowerCase())
                    .contains(h.name().toLowerCase());
        }
    }

    @Test
    @DisplayName("regexAlternation은 (?:nolock|readpast|...) 형태로 그룹화된 정규식이다")
    void regexAlternation_정규식_그룹_형태() {
        String alternation = Hint.regexAlternation();

        assertThat(alternation).startsWith("(?:").endsWith(")");
        assertThat(Pattern.compile(alternation, Pattern.CASE_INSENSITIVE).matcher("NOLOCK").matches()).isTrue();
        assertThat(Pattern.compile(alternation, Pattern.CASE_INSENSITIVE).matcher("readpast").matches()).isTrue();
    }

    @Test
    @DisplayName("regexAlternation은 enum과 동일한 개수의 토큰을 가진다")
    void regexAlternation_토큰_개수() {
        String alternation = Hint.regexAlternation();
        // (?:a|b|c|d) 형태에서 | 개수 = enum 개수 - 1
        long pipeCount = alternation.chars().filter(c -> c == '|').count();
        assertThat(pipeCount).isEqualTo(Hint.values().length - 1);
    }
}

package io.github.jpamssqlhints.inspector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TablePatternMatcher — 화이트/블랙리스트 매칭 단위 테스트")
class TablePatternMatcherTest {

    @Nested
    @DisplayName("빈 입력 / 비어있음 처리")
    class 빈_입력 {

        @Test
        @DisplayName("null 패턴 리스트면 isEmpty=true, 어떤 입력에도 매칭 안 됨")
        void null_리스트() {
            TablePatternMatcher m = new TablePatternMatcher(null);
            assertThat(m.isEmpty()).isTrue();
            assertThat(m.matches("anything")).isFalse();
        }

        @Test
        @DisplayName("빈 패턴 리스트면 isEmpty=true")
        void 빈_리스트() {
            TablePatternMatcher m = new TablePatternMatcher(Collections.emptyList());
            assertThat(m.isEmpty()).isTrue();
            assertThat(m.matches("member")).isFalse();
        }

        @Test
        @DisplayName("패턴 안에 null/blank 항목이 섞여 있으면 무시")
        void null_또는_blank_항목_무시() {
            TablePatternMatcher m = new TablePatternMatcher(Arrays.asList(null, "", "  ", "member"));
            assertThat(m.matches("member")).isTrue();
            assertThat(m.matches("other")).isFalse();
        }

        @Test
        @DisplayName("매칭할 테이블 이름이 null이면 false")
        void null_테이블_이름() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("member"));
            assertThat(m.matches(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("정확 매칭")
    class 정확_매칭 {

        @Test
        @DisplayName("동일한 이름은 매칭")
        void 동일_매칭() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("payment"));
            assertThat(m.matches("payment")).isTrue();
        }

        @Test
        @DisplayName("대소문자 무관")
        void 대소문자_무관() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("Payment"));
            assertThat(m.matches("PAYMENT")).isTrue();
            assertThat(m.matches("payment")).isTrue();
        }

        @Test
        @DisplayName("부분 일치는 매칭 안 됨 — payment 패턴이 payments에 매칭되면 안 됨")
        void 부분_일치_방지() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("payment"));
            assertThat(m.matches("payments")).isFalse();
            assertThat(m.matches("xpayment")).isFalse();
        }

        @Test
        @DisplayName("앞뒤 공백은 trim되어 비교")
        void 패턴_앞뒤_공백_trim() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("  member  "));
            assertThat(m.matches("member")).isTrue();
        }
    }

    @Nested
    @DisplayName("glob 와일드카드")
    class glob {

        @ParameterizedTest(name = "[{index}] stat_* → {0}")
        @ValueSource(strings = {"stat_daily", "stat_monthly", "stat_user_summary"})
        @DisplayName("prefix 와일드카드")
        void prefix(String table) {
            TablePatternMatcher m = new TablePatternMatcher(List.of("stat_*"));
            assertThat(m.matches(table)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] *_log → {0}")
        @ValueSource(strings = {"access_log", "audit_log", "request_log"})
        @DisplayName("suffix 와일드카드")
        void suffix(String table) {
            TablePatternMatcher m = new TablePatternMatcher(List.of("*_log"));
            assertThat(m.matches(table)).isTrue();
        }

        @Test
        @DisplayName("중간 와일드카드 *audit*")
        void 중간() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("*audit*"));
            assertThat(m.matches("user_audit_log")).isTrue();
            assertThat(m.matches("audit")).isTrue();
            assertThat(m.matches("auditing")).isTrue();
            assertThat(m.matches("member")).isFalse();
        }

        @Test
        @DisplayName("* 단독은 모든 이름과 매칭")
        void 와일드카드_단독() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("*"));
            assertThat(m.matches("anything")).isTrue();
            assertThat(m.matches("a")).isTrue();
        }

        @Test
        @DisplayName("glob 매칭도 대소문자 무관")
        void glob_대소문자() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("STAT_*"));
            assertThat(m.matches("stat_daily")).isTrue();
        }

        @Test
        @DisplayName("패턴에 매칭되지 않는 이름은 false")
        void 미매칭() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("stat_*"));
            assertThat(m.matches("member")).isFalse();
        }
    }

    @Nested
    @DisplayName("정규식 메타 문자 escape — 글롭 외 다른 메타는 리터럴로 취급")
    class 정규식_메타_escape {

        @Test
        @DisplayName("점은 escape되어 리터럴로 비교됨")
        void 점은_리터럴() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("a.b"));
            assertThat(m.matches("a.b")).isTrue();
            assertThat(m.matches("axb"))
                    .as("점이 정규식 . 으로 동작하면 안 됨")
                    .isFalse();
        }

        @Test
        @DisplayName("괄호/플러스/물음표 등도 리터럴")
        void 다른_메타_리터럴() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("a+b"));
            assertThat(m.matches("a+b")).isTrue();
            assertThat(m.matches("aab")).isFalse();
        }
    }

    @Nested
    @DisplayName("여러 패턴 — 하나라도 매칭되면 true")
    class 여러_패턴 {

        @Test
        @DisplayName("OR 결합 동작")
        void OR_결합() {
            TablePatternMatcher m = new TablePatternMatcher(List.of("member", "payment_*"));
            assertThat(m.matches("member")).isTrue();
            assertThat(m.matches("payment_history")).isTrue();
            assertThat(m.matches("audit")).isFalse();
        }
    }
}

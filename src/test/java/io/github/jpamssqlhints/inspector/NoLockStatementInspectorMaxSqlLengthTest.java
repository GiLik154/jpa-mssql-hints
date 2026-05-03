package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.config.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("maxSqlLength로 매우 긴 SQL의 ReDoS/CPU 폭주 방어")
class NoLockStatementInspectorMaxSqlLengthTest {

    @Test
    @DisplayName("maxSqlLength=0(기본)이면 길이 무제한 — 변환 정상")
    void 기본값은_무제한() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .build();
        String sql = "select * from member";

        String result = inspector.inspect(sql);

        assertThat(result.toLowerCase()).contains("with (nolock)");
    }

    @Test
    @DisplayName("maxSqlLength를 초과하는 SQL은 변환 없이 원본 반환")
    void 길이_초과_SQL은_스킵() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .maxSqlLength(50)
                                                                     .build();
        String longSql = "select " + "a, ".repeat(100) + "x from member";

        String result = inspector.inspect(longSql);

        assertThat(result).isEqualTo(longSql);
        assertThat(result.toLowerCase()).doesNotContain("with (nolock)");
    }

    @Test
    @DisplayName("maxSqlLength 이내 SQL은 정상 변환")
    void 길이_이내_SQL은_변환() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                                                                     .mode(Mode.GLOBAL)
                                                                     .maxSqlLength(1000)
                                                                     .build();
        String sql = "select * from member";

        String result = inspector.inspect(sql);

        assertThat(result.toLowerCase()).contains("with (nolock)");
    }
}

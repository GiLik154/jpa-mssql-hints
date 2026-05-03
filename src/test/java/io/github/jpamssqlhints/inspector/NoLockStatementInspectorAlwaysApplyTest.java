package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.config.Mode;
import io.github.jpamssqlhints.context.HintContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockStatementInspectorAlwaysApplyTest {

    @AfterEach
    void cleanup() {
        while (HintContext.isActive()) {
            HintContext.exit();
        }
    }

    @Test
    @DisplayName("ANNOTATION 모드에서 어노테이션 없어도 화이트리스트 테이블에는 NOLOCK 적용")
    void 어노테이션_없이도_화이트는_적용() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.ANNOTATION)
                .alwaysApplyTables(List.of("dashboard_summary"))
                .build();
        // NoLockContext 비활성 상태
        String result = inspector.inspect("select * from dashboard_summary where dt = '2026-05-03'");
        assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("화이트리스트 외 테이블은 ANNOTATION 비활성에서 미적용 (기존 동작)")
    void 화이트_외는_그대로() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.ANNOTATION)
                .alwaysApplyTables(List.of("dashboard_summary"))
                .build();
        String sql = "select * from member where id = 1";
        assertThat(inspector.inspect(sql)).isEqualTo(sql);
    }

    @Test
    @DisplayName("다중 JOIN에서 화이트 테이블에만 NOLOCK 적용")
    void JOIN_부분_적용() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.ANNOTATION)
                .alwaysApplyTables(List.of("dashboard_summary"))
                .build();
        String result = inspector.inspect(
                "select * from member m inner join dashboard_summary d on m.id = d.member_id"
        );
        assertThat(result)
                .as("화이트 테이블에만 NOLOCK")
                .contains("join dashboard_summary d WITH (NOLOCK)");
        assertThat(result)
                .as("화이트 외 테이블에는 NOLOCK 없음")
                .doesNotContain("from member m WITH (NOLOCK)");
    }

    @Test
    @DisplayName("화이트리스트 매칭은 대소문자 무관")
    void 대소문자_무관() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.ANNOTATION)
                .alwaysApplyTables(List.of("Dashboard_Summary"))
                .build();
        String result = inspector.inspect("select * from DASHBOARD_SUMMARY where dt = '2026-05-03'");
        assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("OFF 모드에서는 화이트리스트도 무시 (kill switch 우선)")
    void OFF는_화이트도_무시() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.OFF)
                .alwaysApplyTables(List.of("dashboard_summary"))
                .build();
        String sql = "select * from dashboard_summary where dt = '2026-05-03'";
        assertThat(inspector.inspect(sql)).isEqualTo(sql);
    }

    @Test
    @DisplayName("같은 테이블이 화이트 + 블랙에 동시 매칭되면 블랙리스트가 우선")
    void 블랙리스트_우선() {
        NoLockStatementInspector inspector = NoLockStatementInspector.builder()
                .mode(Mode.GLOBAL)
                .excludeTables(List.of("dashboard_summary"))
                .alwaysApplyTables(List.of("dashboard_summary"))
                .build();
        String sql = "select * from dashboard_summary where dt = '2026-05-03'";
        assertThat(inspector.inspect(sql)).doesNotContainIgnoringCase("WITH (NOLOCK)");
    }
}

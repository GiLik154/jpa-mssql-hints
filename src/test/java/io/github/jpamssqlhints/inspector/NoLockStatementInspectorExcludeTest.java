package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.config.Mode;
import io.github.jpamssqlhints.context.NoLockContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockStatementInspectorExcludeTest {

    @AfterEach
    void cleanup() {
        while (NoLockContext.isActive()) {
            NoLockContext.exit();
        }
    }

    @Test
    @DisplayName("블랙리스트 테이블에는 NOLOCK이 붙지 않는다")
    void 블랙리스트_제외() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of("payment")
        );
        String result = inspector.inspect("select * from payment where id = 1");
        assertThat(result).doesNotContainIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("블랙리스트 외 테이블은 정상 적용")
    void 블랙리스트_외_적용() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of("payment")
        );
        String result = inspector.inspect("select * from member where id = 1");
        assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("다중 JOIN에서 블랙리스트 테이블만 NOLOCK 제외")
    void 다중_조인에서_부분_제외() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of("payment")
        );
        String result = inspector.inspect(
                "select * from member m inner join payment p on m.id = p.member_id"
        );
        assertThat(result)
                .as("member에는 NOLOCK이 붙어야 함")
                .contains("from member m WITH (NOLOCK)");
        assertThat(result)
                .as("payment에는 NOLOCK이 붙으면 안 됨")
                .doesNotContain("payment p WITH (NOLOCK)");
    }

    @Test
    @DisplayName("블랙리스트 매칭은 대소문자 무관")
    void 대소문자_무관() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of("Payment")
        );
        String result = inspector.inspect("select * from PAYMENT where id = 1");
        assertThat(result).doesNotContainIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("스키마.테이블 형식에서도 테이블명 기준으로 매칭")
    void 스키마_접두사_무시() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of("payment")
        );
        String result = inspector.inspect("select * from dbo.payment where id = 1");
        assertThat(result).doesNotContainIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("대괄호 식별자에서도 테이블명 기준으로 매칭")
    void 대괄호_식별자() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.GLOBAL, List.of("payment")
        );
        String result = inspector.inspect("select * from [dbo].[payment] where id = 1");
        assertThat(result).doesNotContainIgnoringCase("WITH (NOLOCK)");
    }

    @Test
    @DisplayName("ANNOTATION 모드에서도 블랙리스트가 동작")
    void 어노테이션_모드에서도_제외() {
        NoLockStatementInspector inspector = new NoLockStatementInspector(
                Mode.ANNOTATION, List.of("payment")
        );
        NoLockContext.enter();
        String result = inspector.inspect("select * from payment where id = 1");
        assertThat(result).doesNotContainIgnoringCase("WITH (NOLOCK)");
    }
}

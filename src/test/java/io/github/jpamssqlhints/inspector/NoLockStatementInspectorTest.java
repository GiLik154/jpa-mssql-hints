package io.github.jpamssqlhints.inspector;

import io.github.jpamssqlhints.context.NoLockContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NoLockStatementInspectorTest {

    private final NoLockStatementInspector inspector = new NoLockStatementInspector();

    @AfterEach
    void cleanup() {
        while (NoLockContext.isActive()) {
            NoLockContext.exit();
        }
    }

    /**
     * NoLock 컨텍스트가 비활성이면 어떤 SQL이든 손대지 않는다.
     */
    @Nested
    @DisplayName("NoLockContext 비활성 구간")
    class 비활성_구간 {

        @ParameterizedTest
        @ValueSource(strings = {
                "select * from member",
                "select id from member where id = 1",
                "select id from member m inner join orders o on m.id = o.member_id",
                "insert into member values (1, 'a')",
                "update member set name = 'b' where id = 1",
                ""
        })
        @DisplayName("어떤 SQL이든 그대로 반환")
        void 그대로_반환(String sql) {
            assertThat(inspector.inspect(sql)).isEqualTo(sql);
        }

        @Test
        @DisplayName("null도 그대로 통과")
        void null_은_null() {
            assertThat(inspector.inspect(null)).isNull();
        }
    }

    /**
     * NoLock 활성 + SELECT 외 SQL 타입은 손대지 않는다.
     * (SELECT만 NOLOCK이 의미가 있고, DML에 NOLOCK 붙이면 SQL 자체가 무효)
     */
    @Nested
    @DisplayName("NoLockContext 활성 + SELECT가 아닌 경우")
    class SELECT가_아닌_SQL {

        @BeforeEach
        void enter() {
            NoLockContext.enter();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "insert into member (id, name) values (1, 'a')",
                "INSERT INTO member SELECT * FROM source",
                "update member set name = 'b' where id = 1",
                "UPDATE member SET name = 'b' FROM other o WHERE o.id = member.id",
                "delete from member where id = 1",
                "DELETE FROM member WHERE id = 1",
                "merge into target using source on target.id = source.id when matched then update set target.name = source.name",
                "exec sp_help member",
                "create table member (id int)",
                "drop table member"
        })
        @DisplayName("DML/DDL은 변환되지 않음")
        void 변환_안_됨(String sql) {
            assertThat(inspector.inspect(sql)).isEqualTo(sql);
        }
    }

    /**
     * SELECT 문에 단순 FROM 절이 있는 다양한 식별자 형태를 검증.
     */
    @Nested
    @DisplayName("FROM 절 - 식별자 형태")
    class FROM_식별자_형태 {

        @BeforeEach
        void enter() {
            NoLockContext.enter();
        }

        @ParameterizedTest(name = "[{index}] {0} → {1}")
        @CsvSource(delimiter = '|', value = {
                "select * from member                                | select * from member WITH (NOLOCK)",
                "select id from member where id = 1                  | select id from member WITH (NOLOCK) where id = 1",
                "select id from member m where m.id = 1              | select id from member m WITH (NOLOCK) where m.id = 1",
                "select id from member as m where m.id = 1           | select id from member as m WITH (NOLOCK) where m.id = 1",
                "select id from dbo.member where id = 1              | select id from dbo.member WITH (NOLOCK) where id = 1",
                "select id from dbo.member m where m.id = 1          | select id from dbo.member m WITH (NOLOCK) where m.id = 1",
                "select id from [member] where id = 1                | select id from [member] WITH (NOLOCK) where id = 1",
                "select id from [dbo].[member] where id = 1          | select id from [dbo].[member] WITH (NOLOCK) where id = 1",
                "select id from [dbo].[member] m where m.id = 1      | select id from [dbo].[member] m WITH (NOLOCK) where m.id = 1"
        })
        @DisplayName("alias / 스키마 / 대괄호 모든 조합")
        void 다양한_식별자_변환(String input, String expected) {
            assertThat(inspector.inspect(input)).isEqualToIgnoringCase(expected);
        }
    }

    /**
     * SELECT 문에서 대소문자 / 공백 / 줄바꿈 변형이 있어도 동작해야 한다.
     */
    @Nested
    @DisplayName("FROM 절 - 대소문자 및 공백 변형")
    class FROM_공백_대소문자 {

        @BeforeEach
        void enter() {
            NoLockContext.enter();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "SELECT * FROM member WHERE id = 1",
                "Select * From member Where id = 1",
                "select * FROM member where id = 1"
        })
        @DisplayName("FROM 키워드 대소문자 무관")
        void 대소문자(String sql) {
            String result = inspector.inspect(sql);
            assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
        }

        @Test
        @DisplayName("FROM과 테이블 사이 다중 공백")
        void 다중_공백() {
            String result = inspector.inspect("select * from   member where id = 1");
            assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
            assertThat(result).contains("from   member");
        }

        @Test
        @DisplayName("FROM과 테이블 사이 탭")
        void 탭_문자() {
            String result = inspector.inspect("select * from\tmember where id = 1");
            assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
        }

        @Test
        @DisplayName("FROM 앞뒤 줄바꿈")
        void 줄바꿈() {
            String sql = "select *\nfrom member\nwhere id = 1";
            String result = inspector.inspect(sql);
            assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
        }

        @Test
        @DisplayName("선두 공백/줄바꿈이 있어도 SELECT로 인식")
        void 선두_공백() {
            String result = inspector.inspect("   \n\t select * from member");
            assertThat(result).containsIgnoringCase("WITH (NOLOCK)");
        }
    }

    /**
     * JOIN 종류별로 모두 동작해야 한다.
     */
    @Nested
    @DisplayName("JOIN 절 - 모든 종류")
    class JOIN_종류 {

        @BeforeEach
        void enter() {
            NoLockContext.enter();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "select m.id from member m inner join orders o on m.id = o.member_id",
                "select m.id from member m left join orders o on m.id = o.member_id",
                "select m.id from member m right join orders o on m.id = o.member_id",
                "select m.id from member m full join orders o on m.id = o.member_id",
                "select m.id from member m cross join orders o",
                "select m.id from member m left outer join orders o on m.id = o.member_id",
                "select m.id from member m join orders o on m.id = o.member_id"
        })
        @DisplayName("INNER/LEFT/RIGHT/FULL/CROSS/OUTER/순수 JOIN 모두에 NOLOCK 삽입")
        void 모든_조인_종류(String sql) {
            String result = inspector.inspect(sql);
            assertThat(result).contains("from member m WITH (NOLOCK)");
            assertThat(result).contains("join orders o WITH (NOLOCK)");
        }

        @Test
        @DisplayName("3개 이상 다중 JOIN 모든 테이블에 NOLOCK")
        void 다중_조인() {
            String sql = "select * from a a1 inner join b b1 on a1.id = b1.id "
                    + "left join c c1 on b1.id = c1.id "
                    + "right join d d1 on c1.id = d1.id";
            String result = inspector.inspect(sql);
            assertThat(result).contains("from a a1 WITH (NOLOCK)");
            assertThat(result).contains("join b b1 WITH (NOLOCK)");
            assertThat(result).contains("join c c1 WITH (NOLOCK)");
            assertThat(result).contains("join d d1 WITH (NOLOCK)");
        }
    }

    /**
     * RESERVED 키워드 목록의 각 단어는 alias로 잡히면 안 된다.
     * 즉 "from member <RESERVED>" 형태에서 <RESERVED>가 alias로 흡수되면 안 됨.
     */
    @Nested
    @DisplayName("RESERVED 키워드는 alias로 흡수되지 않음")
    class RESERVED_키워드_alias_방지 {

        @BeforeEach
        void enter() {
            NoLockContext.enter();
        }

        @ParameterizedTest(name = "[{index}] from member {0} ...")
        @ValueSource(strings = {
                "where id = 1",
                "group by id",
                "order by id",
                "having count(*) > 0",
                "inner join orders o on m.id = o.member_id",
                "left join orders o on m.id = o.member_id",
                "right join orders o on m.id = o.member_id",
                "full join orders o on m.id = o.member_id",
                "cross join orders",
                "outer apply (select 1) x",
                "join orders o on m.id = o.member_id",
                "union select 1",
                "intersect select 1",
                "except select 1",
                "for update",
                "option (recompile)"
        })
        @DisplayName("RESERVED 키워드를 alias로 잡지 않고 테이블 직후에 NOLOCK 삽입")
        void reserved_키워드_뒤(String tail) {
            String sql = "select * from member " + tail;
            String result = inspector.inspect(sql);
            assertThat(result)
                    .as("입력: %s", sql)
                    .contains("from member WITH (NOLOCK)");
            // RESERVED 키워드 자체가 alias로 잡혔다면 잘못된 위치에 NOLOCK이 박힘
            assertThat(result).doesNotContainIgnoringCase("WITH (NOLOCK) WITH");
        }
    }

    /**
     * 이미 NOLOCK이 적용된 SQL은 절대 두 번 변환하지 않는다.
     */
    @Nested
    @DisplayName("중복 NOLOCK 삽입 방지")
    class 중복_방지 {

        @BeforeEach
        void enter() {
            NoLockContext.enter();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "select * from member WITH (NOLOCK) where id = 1",
                "select * from member with (nolock) where id = 1",
                "select * from member WITH(NOLOCK) where id = 1",
                "select * from member with(nolock) where id = 1",
                "select * from member WITH (NoLock) where id = 1",
                "select m.id from member m WITH (NOLOCK) inner join orders o WITH (NOLOCK) on m.id = o.member_id"
        })
        @DisplayName("이미 NOLOCK이 있는 SQL은 그대로 반환")
        void 그대로_반환(String sql) {
            String result = inspector.inspect(sql);
            assertThat(result).isEqualTo(sql);
            int count = countOccurrences(result.toLowerCase(), "nolock");
            assertThat(count)
                    .as("NOLOCK 등장 횟수가 입력과 같아야 함")
                    .isEqualTo(countOccurrences(sql.toLowerCase(), "nolock"));
        }

        private int countOccurrences(String text, String token) {
            int count = 0;
            int idx = 0;
            while ((idx = text.indexOf(token, idx)) != -1) {
                count++;
                idx += token.length();
            }
            return count;
        }
    }

    /**
     * 정규식의 알려진 한계를 명시적으로 문서화한다. 이 테스트는 "이렇게
     * 동작한다"는 사실 자체가 사용자에게 의미가 있다.
     */
    @Nested
    @DisplayName("알려진 한계 - 문서화 목적의 테스트")
    class 알려진_한계 {

        @BeforeEach
        void enter() {
            NoLockContext.enter();
        }

        @Test
        @DisplayName("CTE(WITH 절)로 시작하는 쿼리는 SELECT로 인식하지 않아 변환 안 됨")
        void CTE_미지원() {
            String sql = "with cte as (select id from member) select * from cte";
            // SELECT_HEAD가 ^select만 인식하므로 CTE는 그대로 통과
            String result = inspector.inspect(sql);
            assertThat(result).isEqualTo(sql);
        }

        @Test
        @DisplayName("문자열 리터럴 안의 from은 우리가 막을 수 없음 (정규식 한계)")
        void 문자열_리터럴_안의_from() {
            // 'from member'는 그냥 문자열인데 정규식이 이를 구분 못 함.
            // 사용자는 native query에서 이런 케이스를 주의해야 한다.
            String sql = "select 'hello from member' as msg from logs";
            String result = inspector.inspect(sql);
            // 실제 from logs에는 NOLOCK이 붙어야 함
            assertThat(result).contains("from logs WITH (NOLOCK)");
        }

        @Test
        @DisplayName("서브쿼리의 FROM에도 NOLOCK이 붙는다(의도된 동작)")
        void 서브쿼리_FROM도_변환() {
            String sql = "select * from (select id from member) t";
            String result = inspector.inspect(sql);
            // 서브쿼리 안의 from member에 NOLOCK이 붙음
            assertThat(result).contains("from member WITH (NOLOCK)");
        }
    }

    /**
     * NoLockContext 활성 / 비활성을 토글하면서 같은 inspector 인스턴스가
     * 정상 동작하는지 검증.
     */
    @Test
    @DisplayName("같은 인스턴스가 활성/비활성 토글에 따라 다르게 동작")
    void 활성_비활성_토글() {
        String sql = "select * from member where id = 1";

        // 비활성
        assertThat(inspector.inspect(sql)).isEqualTo(sql);

        // 활성
        NoLockContext.enter();
        assertThat(inspector.inspect(sql)).containsIgnoringCase("WITH (NOLOCK)");

        // 다시 비활성
        NoLockContext.exit();
        assertThat(inspector.inspect(sql)).isEqualTo(sql);
    }
}

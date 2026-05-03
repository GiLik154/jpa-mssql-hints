package io.github.jpamssqlhints.inspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 화이트/블랙리스트 테이블 패턴 매처. 정확 매칭(`payment`)과 glob 와일드카드
 * (`stat_*`, `*_log`, `*audit*`)를 모두 지원한다. 매칭은 대소문자 무관.
 *
 * <p>각 패턴은 정규식으로 사전 컴파일되며, glob의 `*`만 `.*`로 치환되고
 * 다른 정규식 메타 문자는 escape된다. 따라서 `payment`는 `payments`에
 * 매칭되지 않는다 (정확 매칭).
 */
final class TablePatternMatcher {

    private final List<Pattern> patterns;

    TablePatternMatcher(List<String> rawPatterns) {
        if (rawPatterns == null || rawPatterns.isEmpty()) {
            this.patterns = Collections.emptyList();
            return;
        }
        List<Pattern> compiled = new ArrayList<>(rawPatterns.size());
        for (String raw : rawPatterns) {
            if (raw == null || raw.isBlank()) continue;
            compiled.add(toRegex(raw.trim()));
        }
        this.patterns = compiled;
    }

    boolean matches(String tableName) {
        if (patterns.isEmpty() || tableName == null) return false;
        for (Pattern p : patterns) {
            if (p.matcher(tableName).matches()) return true;
        }
        return false;
    }

    boolean isEmpty() {
        return patterns.isEmpty();
    }

    private static Pattern toRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (REGEX_META.indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static final String REGEX_META = "\\.[]{}()<>+-=!?^$|";
}

package core.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, String> testData;

    public DataEngine(Map<String, String> testData) {
        this.testData = testData;
    }

    public String resolve(String value) {
        if (value == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = testData.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return unescapeUnicode(resolved.toString());
    }

    private String unescapeUnicode(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (i + 5 < value.length() && value.charAt(i) == '\\' && value.charAt(i + 1) == 'u') {
                String hex = value.substring(i + 2, i + 6);
                try {
                    result.append((char) Integer.parseInt(hex, 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {
                    // Keep the original characters when this is not a valid unicode escape.
                }
            }
            result.append(value.charAt(i));
        }
        return result.toString();
    }
}

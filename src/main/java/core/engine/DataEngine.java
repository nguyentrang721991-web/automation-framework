package core.engine;

import java.util.Map;

public class DataEngine {

    private Map<String, String> testData;

    // ✅ constructor đúng
    public DataEngine(Map<String, String> testData) {
        this.testData = testData;
    }

    public String resolve(String value) {
        if (value == null) return null;

        if (value.startsWith("${") && value.endsWith("}")) {
            String key = value.substring(2, value.length() - 1);
            return testData.getOrDefault(key, value);
        }

        return value;
    }
}
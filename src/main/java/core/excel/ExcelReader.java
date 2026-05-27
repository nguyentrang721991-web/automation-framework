package core.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ExcelReader implements AutoCloseable {

    private static final String TEST_CASES_SHEET = "TestCases";
    private static final String TEST_DATA_SHEET = "TestData";
    private static final String TEST_STEPS_SHEET = "TestSteps";
    private static final String OBJECT_REPOSITORY_SHEET = "ObjectRepository";
    private static final Set<String> GLOBAL_TEST_DATA_IDS = Set.of("GLOBAL", "COMMON", "DEFAULT", "ALL", "*");

    private final Workbook workbook;
    private final DataFormatter formatter;
    private final FormulaEvaluator evaluator;

    public ExcelReader(String filePath) {
        try (InputStream inputStream = openWorkbookStream(filePath)) {
            workbook = new XSSFWorkbook(inputStream);
            formatter = new DataFormatter();
            evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        } catch (Exception e) {
            throw new RuntimeException("Cannot load Excel file: " + filePath, e);
        }
    }

    private InputStream openWorkbookStream(String filePath) throws IOException {
        File file = new File(filePath);
        IOException fileOpenException = null;
        if (file.exists()) {
            try {
                return new FileInputStream(file);
            } catch (IOException e) {
                fileOpenException = e;
            }
        }

        String resourceName = file.getName();
        InputStream resourceStream = ExcelReader.class.getClassLoader().getResourceAsStream(resourceName);
        if (resourceStream != null) {
            return resourceStream;
        }

        IOException exception = new IOException("Excel file not found/readable on filesystem or classpath: " + filePath);
        if (fileOpenException != null) {
            exception.addSuppressed(fileOpenException);
        }
        throw exception;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        try {
            return formatter.formatCellValue(cell, evaluator).trim();
        } catch (Exception ignored) {
            return formatter.formatCellValue(cell).trim();
        }
    }

    public List<Map<String, String>> getLoginTestCases(String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null && workbook.getSheet(TEST_CASES_SHEET) != null) {
            sheet = workbook.getSheet(TEST_CASES_SHEET);
        }
        if (sheet == null) {
            throw new RuntimeException("Sheet not found: " + sheetName);
        }

        int templateHeaderRowIndex = findHeaderRow(sheet, "TestCaseID");
        if (templateHeaderRowIndex >= 0) {
            return readTemplateTestCases(sheet, templateHeaderRowIndex);
        }

        int headerRowIndex = findHeaderRow(sheet, "No");
        if (headerRowIndex < 0) {
            throw new RuntimeException("Cannot find manual testcase header row in sheet: " + sheetName);
        }

        List<Map<String, String>> testCases = new ArrayList<>();
        String currentLv1 = "";
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            String tcId = getCellValue(row.getCell(0));
            if (tcId.isEmpty() || !tcId.startsWith("PK-")) {
                continue;
            }

            String lv1 = getCellValue(row.getCell(1));
            if (!lv1.isEmpty()) {
                currentLv1 = lv1;
            }

            Map<String, String> tc = new LinkedHashMap<>();
            tc.put("TC_ID", tcId);
            tc.put("LV1", currentLv1);
            tc.put("DESCRIPTION", getCellValue(row.getCell(2)));
            tc.put("EXPECTED", getCellValue(row.getCell(3)));
            tc.put("WEB", getCellValue(row.getCell(4)));
            tc.put("ANDROID", getCellValue(row.getCell(5)));
            tc.put("IOS", getCellValue(row.getCell(6)));
            tc.put("ISSUE", getCellValue(row.getCell(7)));
            tc.put("JIRA", getCellValue(row.getCell(8)));
            tc.put("RUN", "");
            tc.put("HAS_TEST_DATA", String.valueOf(hasTestData(tcId)));
            tc.put("HAS_CUSTOM_STEPS", String.valueOf(hasCustomSteps(tcId)));
            testCases.add(tc);
        }
        return testCases;
    }

    private List<Map<String, String>> readTemplateTestCases(Sheet sheet, int headerRowIndex) {
        Map<String, Integer> columns = indexColumns(sheet.getRow(headerRowIndex));
        Integer tcColumn = firstExisting(columns, "testcaseid", "tc_id", "tcid");
        if (tcColumn == null) {
            throw new RuntimeException("Cannot find TestCaseID column in sheet: " + sheet.getSheetName());
        }

        Integer descriptionColumn = firstExisting(columns, "description", "tcs", "tcslv2", "name");
        Integer runColumn = firstExisting(columns, "run", "execute");
        Integer expectedColumn = firstExisting(columns, "expected", "expect");
        Integer webColumn = firstExisting(columns, "web");

        List<Map<String, String>> testCases = new ArrayList<>();
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            String tcId = getCellValue(row.getCell(tcColumn));
            if (tcId.isEmpty()) {
                continue;
            }

            Map<String, String> tc = new LinkedHashMap<>();
            tc.put("TC_ID", tcId);
            tc.put("LV1", "");
            tc.put("DESCRIPTION", descriptionColumn == null ? "" : getCellValue(row.getCell(descriptionColumn)));
            tc.put("EXPECTED", expectedColumn == null ? "" : getCellValue(row.getCell(expectedColumn)));
            tc.put("WEB", webColumn == null ? "" : getCellValue(row.getCell(webColumn)));
            tc.put("ANDROID", "");
            tc.put("IOS", "");
            tc.put("ISSUE", "");
            tc.put("JIRA", "");
            tc.put("RUN", runColumn == null ? "Y" : getCellValue(row.getCell(runColumn)));
            tc.put("HAS_TEST_DATA", String.valueOf(hasTestData(tcId)));
            tc.put("HAS_CUSTOM_STEPS", String.valueOf(hasCustomSteps(tcId)));
            testCases.add(tc);
        }
        return testCases;
    }

    private int findHeaderRow(Sheet sheet, String firstCellText) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            if (firstCellText.equalsIgnoreCase(getCellValue(row.getCell(0)))) {
                return i;
            }
        }
        return -1;
    }

    public Map<String, String> getTestData(String testCaseId) {
        Map<String, String> data = new LinkedHashMap<>();
        Sheet sheet = workbook.getSheet(TEST_DATA_SHEET);
        if (sheet == null) {
            return data;
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return data;
        }

        Map<String, Integer> columns = indexColumns(header);
        Integer keyColumn = firstExisting(columns, "key", "locatorvalue", "locator_value", "name");
        Integer valueColumn = firstExisting(columns, "value", "data");
        Integer tcColumn = firstExisting(columns, "testcaseid", "tc_id", "tcid");
        if (keyColumn == null || valueColumn == null) {
            return data;
        }

        if (tcColumn == null) {
            readKeyValueTestDataRows(sheet, data, testCaseId, keyColumn, valueColumn, false);
            readKeyValueTestDataRows(sheet, data, testCaseId, keyColumn, valueColumn, true);
        } else {
            readLegacyTestDataRows(sheet, data, testCaseId, tcColumn, keyColumn, valueColumn, true);
            readLegacyTestDataRows(sheet, data, testCaseId, tcColumn, keyColumn, valueColumn, false);
        }
        return data;
    }

    private void readKeyValueTestDataRows(Sheet sheet, Map<String, String> data, String testCaseId,
                                           int keyColumn, int valueColumn, boolean scopedRows) {
        for (Row row : sheet) {
            if (row == null || row.getRowNum() == 0) {
                continue;
            }

            String key = getCellValue(row.getCell(keyColumn));
            if (key.isEmpty()) {
                continue;
            }

            String scopedKey = unscopedTestDataKey(key, testCaseId);
            boolean matchesScope = scopedKey != null;
            if (scopedRows != matchesScope) {
                continue;
            }

            if (!scopedRows && isAnyScopedTestDataKey(key)) {
                continue;
            }

            String value = getCellValue(row.getCell(valueColumn));
            if (matchesScope) {
                data.put(scopedKey, value);
            }
            data.put(key, value);
        }
    }

    private void readLegacyTestDataRows(Sheet sheet, Map<String, String> data, String testCaseId,
                                        int tcColumn, int keyColumn, int valueColumn, boolean globalRows) {
        for (Row row : sheet) {
            if (row == null || row.getRowNum() == 0) {
                continue;
            }

            String tcId = getCellValue(row.getCell(tcColumn));
            boolean matchesGlobal = isGlobalTestDataId(tcId);
            boolean matchesTestCase = tcId.equalsIgnoreCase(testCaseId);
            if (globalRows != matchesGlobal || (!matchesGlobal && !matchesTestCase)) {
                continue;
            }

            String key = getCellValue(row.getCell(keyColumn));
            String value = getCellValue(row.getCell(valueColumn));
            if (!key.isEmpty()) {
                data.put(key, value);
            }
        }
    }

    private String unscopedTestDataKey(String key, String testCaseId) {
        if (key == null || testCaseId == null || testCaseId.isBlank()) {
            return null;
        }

        String normalizedKey = key.trim();
        for (String delimiter : List.of(".", ":")) {
            String prefix = testCaseId + delimiter;
            if (normalizedKey.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String scopedKey = normalizedKey.substring(prefix.length()).trim();
                return scopedKey.isEmpty() ? null : scopedKey;
            }
        }
        return null;
    }

    private boolean isAnyScopedTestDataKey(String key) {
        return key != null && key.trim().matches("(?i)^PK-\\d{3}[.:].+");
    }

    public boolean hasRequiredLoginData(String testCaseId) {
        Map<String, String> data = getTestData(testCaseId);
        return hasValue(data, "email") && hasValue(data, "password");
    }

    public String getAutomationSkipReason(Map<String, String> testCase) {
        String tcId = testCase.getOrDefault("TC_ID", "");
        String run = testCase.getOrDefault("RUN", "");
        String webStatus = testCase.getOrDefault("WEB", "");

        if (!run.isBlank() && !isYes(run)) {
            return "Run is not Y in TestCases sheet";
        }

        if ("not supported".equalsIgnoreCase(webStatus)) {
            return "Web status is Not Supported in manual sheet";
        }

        if (hasCustomSteps(tcId)) {
            return "";
        }

        return "Missing TestSteps for " + tcId;
    }

    private boolean hasValue(Map<String, String> data, String key) {
        String value = data.get(key);
        return value != null && !value.trim().isEmpty();
    }

    private boolean isYes(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("y") || normalized.equals("yes") || normalized.equals("true") || normalized.equals("1");
    }

    private boolean isGlobalTestDataId(String value) {
        return value != null && GLOBAL_TEST_DATA_IDS.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private boolean hasTestData(String testCaseId) {
        return !getTestData(testCaseId).isEmpty();
    }

    private boolean hasCustomSteps(String testCaseId) {
        return !readCustomSteps(testCaseId).isEmpty();
    }

    public List<Map<String, String>> getTestStepsForLoginTC(String tcId) {
        return readCustomSteps(tcId);
    }

    private List<Map<String, String>> readCustomSteps(String testCaseId) {
        Sheet sheet = workbook.getSheet(TEST_STEPS_SHEET);
        if (sheet == null) {
            return Collections.emptyList();
        }

        int headerRowIndex = findHeaderRow(sheet, "TC_ID");
        if (headerRowIndex < 0) {
            headerRowIndex = findHeaderRow(sheet, "TestCaseID");
        }
        if (headerRowIndex < 0) {
            headerRowIndex = 0;
        }

        Row header = sheet.getRow(headerRowIndex);
        if (header == null) {
            return Collections.emptyList();
        }

        Map<String, Integer> columns = indexColumns(header);
        Integer tcColumn = firstExisting(columns, "testcaseid", "tc_id", "tcid");
        Integer actionColumn = firstExisting(columns, "action", "keyword");
        if (tcColumn == null || actionColumn == null) {
            throw new RuntimeException("Cannot find TC_ID/Action columns in sheet: " + TEST_STEPS_SHEET);
        }

        Integer runColumn = firstExisting(columns, "run", "execute");
        Integer stepColumn = firstExisting(columns, "step", "stepno", "no");
        Integer targetColumn = firstExisting(columns, "target", "object", "locator");
        Integer inputColumn = firstExisting(columns, "input", "data", "value");
        Integer expectedColumn = firstExisting(columns, "expected", "expect");
        Integer assertionColumn = firstExisting(columns, "assertion", "assert");
        Integer descriptionColumn = firstExisting(columns, "description", "note");

        List<Map<String, String>> steps = new ArrayList<>();
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            String rowTcId = getCellValue(row.getCell(tcColumn));
            if (!testCaseId.equalsIgnoreCase(rowTcId)) {
                continue;
            }

            if (runColumn != null && "N".equalsIgnoreCase(getCellValue(row.getCell(runColumn)))) {
                continue;
            }

            String action = getCellValue(row.getCell(actionColumn));
            if (action.isEmpty()) {
                continue;
            }

            Map<String, String> step = new LinkedHashMap<>();
            String target = targetColumn == null ? "" : getCellValue(row.getCell(targetColumn));
            String input = inputColumn == null ? "" : getCellValue(row.getCell(inputColumn));
            String stepNumber = stepColumn == null ? String.valueOf(steps.size() + 1) : getCellValue(row.getCell(stepColumn));
            step.put("tc_id", rowTcId);
            step.put("step", stepNumber);
            step.put("action", action);
            step.put("keyword", action);
            step.put("target", target);
            step.put("object", target);
            step.put("input", input);
            step.put("data", input);
            step.put("expected", expectedColumn == null ? "" : getCellValue(row.getCell(expectedColumn)));
            step.put("assertion", assertionColumn == null ? "" : getCellValue(row.getCell(assertionColumn)));
            step.put("description", descriptionColumn == null ? "" : getCellValue(row.getCell(descriptionColumn)));
            steps.add(step);
        }

        steps.sort(Comparator.comparingInt(step -> parseInt(step.get("step"), Integer.MAX_VALUE)));
        return steps;
    }

    private Map<String, Integer> indexColumns(Row header) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            String key = normalizeHeader(getCellValue(cell));
            if (!key.isEmpty()) {
                columns.put(key, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private Integer firstExisting(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer column = columns.get(name);
            if (column != null) {
                return column;
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    public List<String> getRunnableTestCases() {
        List<String> list = new ArrayList<>();
        String sheetName = System.getProperty("login.sheet", TEST_CASES_SHEET);
        for (Map<String, String> testCase : getLoginTestCases(sheetName)) {
            if (getAutomationSkipReason(testCase).isEmpty()) {
                list.add(testCase.get("TC_ID"));
            }
        }
        return list;
    }

    public List<Map<String, String>> getTestSteps(String testCaseId) {
        return readCustomSteps(testCaseId);
    }

    public Map<String, String> getObjectRepository() {
        Map<String, String> objects = new LinkedHashMap<>();
        Sheet sheet = workbook.getSheet(OBJECT_REPOSITORY_SHEET);
        if (sheet == null) {
            return objects;
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return objects;
        }

        Map<String, Integer> columns = indexColumns(header);
        Integer nameColumn = firstExisting(columns, "objectname", "object_name", "object", "name", "key");
        Integer typeColumn = firstExisting(columns, "locatortype", "locator_type", "type", "by");
        Integer valueColumn = firstExisting(columns, "locatorvalue", "locator_value", "value", "locator");

        if (nameColumn == null || valueColumn == null) {
            return objects;
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            String objectName = getCellValue(row.getCell(nameColumn));
            String locatorValue = getCellValue(row.getCell(valueColumn));
            if (objectName.isEmpty() || locatorValue.isEmpty()) {
                continue;
            }

            String locatorType = typeColumn == null ? "" : getCellValue(row.getCell(typeColumn));
            objects.put(objectName, toLocator(locatorType, locatorValue));
        }
        return objects;
    }

    private String toLocator(String locatorType, String locatorValue) {
        String type = locatorType == null ? "" : locatorType.trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            return locatorValue;
        }
        if (locatorValue.toLowerCase(Locale.ROOT).startsWith(type + "=")) {
            return locatorValue;
        }
        return type + "=" + locatorValue;
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    @Override
    public void close() throws IOException {
        workbook.close();
    }
}

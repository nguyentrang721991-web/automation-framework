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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExcelReader implements AutoCloseable {

    private static final String TEST_DATA_SHEET = "TestData";
    private static final String TEST_STEPS_SHEET = "TestSteps";

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
        if (sheet == null) {
            throw new RuntimeException("Sheet not found: " + sheetName);
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

        for (Row row : sheet) {
            if (row == null || row.getRowNum() == 0) {
                continue;
            }

            String tcId = getCellValue(row.getCell(0));
            if (!tcId.equalsIgnoreCase(testCaseId)) {
                continue;
            }

            String key = getCellValue(row.getCell(1));
            String value = getCellValue(row.getCell(2));
            if (!key.isEmpty()) {
                data.put(key, value);
            }
        }
        return data;
    }

    public boolean hasRequiredLoginData(String testCaseId) {
        Map<String, String> data = getTestData(testCaseId);
        return hasValue(data, "email") && hasValue(data, "password");
    }

    public String getAutomationSkipReason(Map<String, String> testCase) {
        String tcId = testCase.getOrDefault("TC_ID", "");
        String webStatus = testCase.getOrDefault("WEB", "");

        if ("not supported".equalsIgnoreCase(webStatus)) {
            return "Web status is Not Supported in manual sheet";
        }

        if (hasCustomSteps(tcId)) {
            return "";
        }

        if (!hasRequiredLoginData(tcId)) {
            return "Missing TestData email/password or TestSteps for " + tcId;
        }

        return "";
    }

    private boolean hasValue(Map<String, String> data, String key) {
        String value = data.get(key);
        return value != null && !value.trim().isEmpty();
    }

    private boolean hasTestData(String testCaseId) {
        return !getTestData(testCaseId).isEmpty();
    }

    private boolean hasCustomSteps(String testCaseId) {
        return !readCustomSteps(testCaseId).isEmpty();
    }

    public List<Map<String, String>> getTestStepsForLoginTC(String tcId) {
        List<Map<String, String>> customSteps = readCustomSteps(tcId);
        if (!customSteps.isEmpty()) {
            return customSteps;
        }
        return defaultLoginSteps(tcId);
    }

    private List<Map<String, String>> readCustomSteps(String testCaseId) {
        Sheet sheet = workbook.getSheet(TEST_STEPS_SHEET);
        if (sheet == null) {
            return Collections.emptyList();
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return Collections.emptyList();
        }

        Map<String, Integer> columns = indexColumns(header);
        Integer tcColumn = firstExisting(columns, "testcaseid", "tc_id", "tcid");
        Integer keywordColumn = firstExisting(columns, "keyword", "action");
        if (tcColumn == null || keywordColumn == null) {
            return Collections.emptyList();
        }

        Integer runColumn = firstExisting(columns, "run", "execute");
        Integer objectColumn = firstExisting(columns, "object", "locator");
        Integer dataColumn = firstExisting(columns, "data", "value", "input");

        List<Map<String, String>> steps = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
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

            String keyword = getCellValue(row.getCell(keywordColumn));
            if (keyword.isEmpty()) {
                continue;
            }

            Map<String, String> step = new LinkedHashMap<>();
            step.put("keyword", keyword);
            step.put("object", objectColumn == null ? "" : getCellValue(row.getCell(objectColumn)));
            step.put("data", dataColumn == null ? "" : getCellValue(row.getCell(dataColumn)));
            steps.add(step);
        }
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

    private List<Map<String, String>> defaultLoginSteps(String tcId) {
        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(step("navigate", "", "${base.url}"));
        steps.add(step("loginwithmicrosoft", "", "${email}|${password}"));
        steps.add(step("entermicrosoftotp", "", "${microsoftOtp}"));

        if ("PK-001".equalsIgnoreCase(tcId)) {
            steps.add(step("assertanytext", "", "B\u1ecf qua|C\u00e0i \u0111\u1eb7t|M\u00e3 b\u1ea3o m\u1eadt|Security"));
        } else if ("PK-002".equalsIgnoreCase(tcId)) {
            steps.add(step("skipsecuritykeysetup", "", ""));
            steps.add(step("assertanytext", "", "H\u1ed9i tho\u1ea1i|Tin nh\u1eafn|Chat|Conversation"));
        } else if ("PK-003".equalsIgnoreCase(tcId)) {
            steps.add(step("skipsecuritykeysetup", "", ""));
            steps.add(step("opencreategroupchat", "", ""));
            steps.add(step("clicksecuritylock", "", ""));
            steps.add(step("assertmessagevisible", "", ""));
        } else {
            steps.add(step("assertanytext", "", "H\u1ed9i tho\u1ea1i|Tin nh\u1eafn|Chat|M\u00e3 PIN|M\u00e3 b\u1ea3o m\u1eadt"));
        }

        return steps;
    }

    private Map<String, String> step(String keyword, String object, String data) {
        Map<String, String> step = new LinkedHashMap<>();
        step.put("keyword", keyword);
        step.put("object", object);
        step.put("data", data);
        return step;
    }

    public List<String> getRunnableTestCases() {
        List<String> list = new ArrayList<>();
        for (Map<String, String> testCase : getLoginTestCases("M2.Login - Encrypt update")) {
            if (getAutomationSkipReason(testCase).isEmpty()) {
                list.add(testCase.get("TC_ID"));
            }
        }
        return list;
    }

    public List<Map<String, String>> getTestSteps(String testCaseId) {
        return readCustomSteps(testCaseId);
    }

    @Override
    public void close() throws IOException {
        workbook.close();
    }
}

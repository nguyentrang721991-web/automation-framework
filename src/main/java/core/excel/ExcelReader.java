package core.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.util.*;

public class ExcelReader {

    private Workbook workbook;

    // =========================
    // CONSTRUCTOR
    // =========================
    public ExcelReader(String filePath) {
        try {
            FileInputStream fis = new FileInputStream(filePath);
            workbook = new XSSFWorkbook(fis);
        } catch (Exception e) {
            throw new RuntimeException("Cannot load Excel file: " + filePath, e);
        }
    }

    // =========================
    // GET RUNNABLE TEST CASES
    // =========================
    public List<String> getRunnableTestCases() {

        List<String> list = new ArrayList<>();
        Sheet sheet = workbook.getSheet("TestCases");

        for (Row row : sheet) {

            if (row == null || row.getRowNum() == 0) continue;

            String tcId = getCellValue(row.getCell(0));
            String run = getCellValue(row.getCell(2));

            if (tcId.isEmpty()) continue;

            if (run.equalsIgnoreCase("Y")) {
                list.add(tcId);
            }
        }

        return list;
    }

    // =========================
    // GET TEST DATA
    // =========================
    public Map<String, String> getTestData(String testCaseId) {

        Map<String, String> data = new HashMap<>();
        Sheet sheet = workbook.getSheet("TestData");

        for (Row row : sheet) {

            if (row == null || row.getRowNum() == 0) continue;

            String tcId = getCellValue(row.getCell(0));

            if (!tcId.equalsIgnoreCase(testCaseId)) continue;

            String key = getCellValue(row.getCell(1));
            String value = getCellValue(row.getCell(2));

            if (!key.isEmpty()) {
                data.put(key, value);
            }
        }

        return data;
    }

    // =========================
    // GET TEST STEPS (FIXED)
    // =========================
    public List<Map<String, String>> getTestSteps(String testCaseId) {

        List<Map<String, String>> steps = new ArrayList<>();
        Sheet sheet = workbook.getSheet("TestSteps");

        for (Row row : sheet) {

            if (row == null || row.getRowNum() == 0) continue;

            String tcId = getCellValue(row.getCell(0));

            if (tcId.isEmpty()) continue;

            if (!tcId.equalsIgnoreCase(testCaseId)) continue;

            // =========================
            // FIXED COLUMN MAPPING
            // Excel: TestCaseID | keyword | object | data
            // =========================
            String keyword = getCellValue(row.getCell(1));
            String object  = getCellValue(row.getCell(2));
            String data    = getCellValue(row.getCell(3));

            // SKIP EMPTY ROW
            if (keyword.isEmpty() && object.isEmpty() && data.isEmpty()) {
                continue;
            }

            Map<String, String> step = new HashMap<>();
            step.put("keyword", keyword);
            step.put("object", object);
            step.put("data", data);

            steps.add(step);
        }

        return steps;
    }

    // =========================
    // SAFE CELL READER (VERY IMPORTANT)
    // =========================
    private String getCellValue(Cell cell) {

        if (cell == null) return "";

        try {
            switch (cell.getCellType()) {

                case STRING:
                    return cell.getStringCellValue().trim();

                case NUMERIC:
                    String num = String.valueOf(cell.getNumericCellValue());
                    return num.replaceAll("\\.0$", "");

                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());

                case FORMULA:
                    return cell.getCellFormula();

                case BLANK:
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
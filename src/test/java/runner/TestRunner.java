package runner;

import core.engine.KeywordEngine;
import core.excel.ExcelReader;
import core.utils.ConfigReader;
import core.utils.DriverFactory;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import utils.AllureListener;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Listeners(AllureListener.class)
public class TestRunner {

    private WebDriver driver;
    private KeywordEngine engine;

    @DataProvider(name = "loginCases")
    public Object[][] loginCases() {
        String sheetName = ConfigReader.getProperty("login.sheet", "TestCases");
        List<Object[]> rows = new ArrayList<>();

        for (Path excelPath : discoverExcelFiles()) {
            try (ExcelReader excel = new ExcelReader(excelPath.toString())) {
                List<Map<String, String>> testCases = excel.getLoginTestCases(sheetName);
                for (Map<String, String> testCase : testCases) {
                    rows.add(new Object[]{new ExcelTestCase(excelPath.toString(), excelPath.getFileName().toString(), testCase)});
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot close Excel file: " + excelPath, e);
            } catch (Exception e) {
                throw new RuntimeException("Cannot load test cases from Excel file: " + excelPath, e);
            }
        }

        if (rows.isEmpty()) {
            throw new RuntimeException("No Excel test cases found. Check excel.dir/excel.pattern or excel.files config.");
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(dataProvider = "loginCases")
    public void runLoginTestCase(ExcelTestCase context) {
        Map<String, String> testCase = context.testCase();
        String tcId = testCase.get("TC_ID");
        String description = testCase.getOrDefault("DESCRIPTION", "");
        System.out.println("\n=== RUNNING " + context.excelFile() + " :: " + tcId + " - " + description + " ===");

        if (!isSelectedForRun(tcId)) {
            throw new SkipException("Testcase is not selected for this run");
        }

        try (ExcelReader excel = new ExcelReader(context.excelPath())) {
            String skipReason = excel.getAutomationSkipReason(testCase);
            if (!skipReason.isEmpty()) {
                throw new SkipException(skipReason);
            }

            Map<String, String> testData = new LinkedHashMap<>(excel.getTestData(tcId));
            testData.putIfAbsent("base.url", ConfigReader.getProperty("base.url"));
            testData.putIfAbsent("excel.file", context.excelFile());
            testData.putIfAbsent("testcase.id", tcId);

            List<Map<String, String>> steps = excel.getTestStepsForLoginTC(tcId);
            driver = DriverFactory.initDriver();
            engine = new KeywordEngine(driver, excel.getObjectRepository());
            engine.executeSteps(steps, testData);
        } catch (IOException e) {
            throw new RuntimeException("Cannot close Excel file: " + context.excelPath(), e);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownDriver() {
        DriverFactory.quitDriver();
    }

    private boolean isSelectedForRun(String tcId) {
        if (tcId == null || tcId.isBlank()) {
            return false;
        }

        Set<String> included = testcaseSet("include.testcases");
        String normalizedTcId = tcId.toUpperCase();

        boolean includedByConfig = included.isEmpty() || included.contains(normalizedTcId);
        boolean skippedByConfig = testcaseSet("skip.testcases").contains(normalizedTcId);
        return includedByConfig && !skippedByConfig;
    }

    private Set<String> testcaseSet(String propertyKey) {
        return Arrays.stream(ConfigReader.getProperty(propertyKey, "").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .flatMap(value -> expandTestcaseToken(value).stream())
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> expandTestcaseToken(String token) {
        String normalized = token.toUpperCase();
        String delimiter = normalized.contains("..") ? "\\.\\." : "-";
        String[] range = normalized.split(delimiter);

        if (range.length == 2 && range[0].matches("PK-\\d+") && range[1].matches("PK-\\d+")) {
            int start = Integer.parseInt(range[0].substring(3));
            int end = Integer.parseInt(range[1].substring(3));
            int step = start <= end ? 1 : -1;

            Set<String> expanded = new LinkedHashSet<>();
            for (int i = start; i != end + step; i += step) {
                expanded.add(String.format("PK-%03d", i));
            }
            return expanded;
        }

        return Set.of(normalized);
    }

    private List<Path> discoverExcelFiles() {
        if (!ConfigReader.getBoolean("excel.autodiscover", true)) {
            List<Path> configuredFiles = configuredExcelFiles();
            if (configuredFiles.isEmpty()) {
                throw new RuntimeException("excel.autodiscover=false but excel.files/excel.path is empty");
            }
            return configuredFiles;
        }

        Path directory = Paths.get(ConfigReader.getProperty("excel.dir", "src/test/resources"));
        String pattern = ConfigReader.getProperty("excel.pattern", "*_TC.xlsx");
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        Set<String> excludedFiles = csvSet(ConfigReader.getProperty("excel.exclude", "Template_TC.xlsx"));

        try {
            if (!Files.isDirectory(directory)) {
                throw new RuntimeException("Excel directory does not exist: " + directory.toAbsolutePath());
            }

            try (var stream = Files.list(directory)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> matcher.matches(path.getFileName()))
                        .filter(path -> !path.getFileName().toString().startsWith("~$"))
                        .filter(path -> !excludedFiles.contains(path.getFileName().toString().toLowerCase()))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot scan Excel directory: " + directory.toAbsolutePath(), e);
        }
    }

    private List<Path> configuredExcelFiles() {
        List<Path> paths = new ArrayList<>();
        String excelFiles = ConfigReader.getProperty("excel.files", "");
        if (!excelFiles.isBlank()) {
            Arrays.stream(excelFiles.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Paths::get)
                    .forEach(paths::add);
            return paths;
        }

        String excelPath = ConfigReader.getProperty("excel.path", "");
        if (!excelPath.isBlank()) {
            paths.add(Paths.get(excelPath));
        }
        return paths;
    }

    private Set<String> csvSet(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.toLowerCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record ExcelTestCase(String excelPath, String excelFile, Map<String, String> testCase) {
        @Override
        public String toString() {
            return excelFile + " :: " + testCase.getOrDefault("TC_ID", "");
        }
    }
}

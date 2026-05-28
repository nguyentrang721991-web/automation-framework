package runner;

import core.engine.KeywordEngine;
import core.excel.ExcelReader;
import core.utils.ConfigReader;
import core.utils.DriverFactory;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import utils.ExtentReportListener;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Listeners(ExtentReportListener.class)
public class TestRunner {

    private WebDriver driver;
    private KeywordEngine engine;

    @DataProvider(name = "excelCases")
    public Object[][] excelCases() {
        String sheetName = ConfigReader.getProperty("testcases.sheet",
                ConfigReader.getProperty("login.sheet", "TestCases"));
        List<Object[]> rows = new ArrayList<>();

        for (ExcelFileContext excelFile : discoverExcelFiles()) {
            try (ExcelReader excel = new ExcelReader(excelFile.path().toString())) {
                List<Map<String, String>> testCases = excel.getTestCases(sheetName);
                for (Map<String, String> testCase : testCases) {
                    rows.add(new Object[]{new ExcelTestCase(
                            excelFile.path().toString(),
                            excelFile.fileName(),
                            excelFile.featureId(),
                            excelFile.featureName(),
                            testCase
                    )});
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot close Excel file: " + excelFile.path(), e);
            } catch (Exception e) {
                throw new RuntimeException("Cannot load test cases from Excel file: " + excelFile.path(), e);
            }
        }

        if (rows.isEmpty()) {
            throw new RuntimeException("No Excel test cases found. Check TestSuite.xlsx RUN flags or excel.dir/excel.pattern/excel.files config.");
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(dataProvider = "excelCases")
    public void runExcelTestCase(ExcelTestCase context) {
        Map<String, String> testCase = context.testCase();
        String tcId = testCase.get("TC_ID");
        String description = testCase.getOrDefault("DESCRIPTION", "");
        String featurePrefix = context.featureId().isBlank() ? "" : context.featureId() + " :: ";
        System.out.println("\n=== RUNNING " + featurePrefix + context.excelFile() + " :: " + tcId + " - " + description + " ===");

        if (!isSelectedForRun(tcId)) {
            throw new SkipException("Testcase is not selected for this run");
        }

        try (ExcelReader excel = new ExcelReader(context.excelPath())) {
            String skipReason = excel.getAutomationSkipReason(testCase);
            if (!skipReason.isEmpty()) {
                throw new SkipException(skipReason);
            }

            Map<String, String> testData = new LinkedHashMap<>(excel.getTestData(tcId));
            testData.putIfAbsent("excel.file", context.excelFile());
            testData.putIfAbsent("feature.id", context.featureId());
            testData.putIfAbsent("feature.name", context.featureName());
            testData.putIfAbsent("testcase.id", tcId);

            List<Map<String, String>> steps = excel.getTestSteps(tcId);
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

    private List<ExcelFileContext> discoverExcelFiles() {
        if (ConfigReader.getBoolean("suite.enabled", true)) {
            return suiteExcelFiles();
        }

        if (!ConfigReader.getBoolean("excel.autodiscover", true)) {
            List<ExcelFileContext> configuredFiles = configuredExcelFiles();
            if (configuredFiles.isEmpty()) {
                throw new RuntimeException("excel.autodiscover=false but excel.files/excel.path is empty");
            }
            return configuredFiles;
        }

        Path directory = Paths.get(ConfigReader.getProperty("excel.dir", "src/test/resources"));
        String pattern = ConfigReader.getProperty("excel.pattern", "*.xlsx");
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        Set<String> excludedFiles = csvSet(ConfigReader.getProperty("excel.exclude", "Template_TC.xlsx,TestSuite.xlsx"));

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
                        .map(this::standaloneExcelFile)
                        .toList();
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot scan Excel directory: " + directory.toAbsolutePath(), e);
        }
    }

    private List<ExcelFileContext> suiteExcelFiles() {
        Path suitePath = Paths.get(ConfigReader.getProperty("suite.path", "src/test/resources/TestSuite.xlsx"));
        String sheetName = ConfigReader.getProperty("suite.sheet", "TestSuite");
        List<ExcelFileContext> excelFiles = new ArrayList<>();
        Set<Path> seenFiles = new LinkedHashSet<>();

        if (!Files.isRegularFile(suitePath)) {
            throw new RuntimeException("Suite file does not exist: " + suitePath.toAbsolutePath());
        }

        try (ExcelReader excel = new ExcelReader(suitePath.toString())) {
            for (Map<String, String> suite : excel.getTestSuites(sheetName)) {
                if (!isYesFlag(suite.getOrDefault("RUN", ""))) {
                    continue;
                }

                String excelFile = suite.getOrDefault("EXCEL_FILE", "").trim();
                if (excelFile.isEmpty()) {
                    continue;
                }

                Path resolvedPath = resolveSuiteExcelPath(suitePath, excelFile);
                if (!Files.isRegularFile(resolvedPath)) {
                    String feature = suite.getOrDefault("FEATURE_ID", "");
                    throw new RuntimeException("Suite feature " + feature + " points to missing Excel file: "
                            + excelFile + " (resolved: " + resolvedPath.toAbsolutePath() + ")");
                }

                Path normalizedPath = resolvedPath.toAbsolutePath().normalize();
                if (seenFiles.add(normalizedPath)) {
                    excelFiles.add(new ExcelFileContext(
                            normalizedPath,
                            suite.getOrDefault("FEATURE_ID", ""),
                            suite.getOrDefault("FEATURE_NAME", "")
                    ));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot close suite file: " + suitePath, e);
        } catch (Exception e) {
            throw new RuntimeException("Cannot load suite file: " + suitePath, e);
        }

        if (excelFiles.isEmpty()) {
            throw new RuntimeException("No RUN=Y feature found in suite file: " + suitePath.toAbsolutePath());
        }
        return excelFiles;
    }

    private Path resolveSuiteExcelPath(Path suitePath, String excelFile) {
        Path rawPath = Paths.get(excelFile);
        if (rawPath.isAbsolute()) {
            return rawPath.normalize();
        }

        Path suiteDirectory = suitePath.toAbsolutePath().normalize().getParent();
        if (suiteDirectory != null) {
            Path fromSuiteDirectory = suiteDirectory.resolve(rawPath).normalize();
            if (Files.exists(fromSuiteDirectory)) {
                return fromSuiteDirectory;
            }
        }

        Path excelDirectory = Paths.get(ConfigReader.getProperty("excel.dir", "src/test/resources"));
        return excelDirectory.resolve(rawPath).toAbsolutePath().normalize();
    }

    private List<ExcelFileContext> configuredExcelFiles() {
        List<ExcelFileContext> paths = new ArrayList<>();
        String excelFiles = ConfigReader.getProperty("excel.files", "");
        if (!excelFiles.isBlank()) {
            Arrays.stream(excelFiles.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Paths::get)
                    .map(this::standaloneExcelFile)
                    .forEach(paths::add);
            return paths;
        }

        String excelPath = ConfigReader.getProperty("excel.path", "");
        if (!excelPath.isBlank()) {
            paths.add(standaloneExcelFile(Paths.get(excelPath)));
        }
        return paths;
    }

    private ExcelFileContext standaloneExcelFile(Path path) {
        return new ExcelFileContext(path.toAbsolutePath().normalize(), "", "");
    }

    private Set<String> csvSet(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.toLowerCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isYesFlag(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("y") || normalized.equals("yes") || normalized.equals("true") || normalized.equals("1");
    }

    private record ExcelFileContext(Path path, String featureId, String featureName) {
        private String fileName() {
            return path.getFileName().toString();
        }
    }

    private record ExcelTestCase(String excelPath, String excelFile, String featureId, String featureName,
                                 Map<String, String> testCase) {
        @Override
        public String toString() {
            String featurePrefix = featureId.isBlank() ? "" : featureId + " :: ";
            return featurePrefix + excelFile + " :: " + testCase.getOrDefault("TC_ID", "");
        }
    }
}

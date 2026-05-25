package runner;

import core.engine.KeywordEngine;
import core.excel.ExcelReader;
import core.utils.ConfigReader;
import core.utils.DriverFactory;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import utils.AllureListener;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Listeners(AllureListener.class)
public class TestRunner {

    private ExcelReader excel;
    private WebDriver driver;
    private KeywordEngine engine;

    @BeforeClass(alwaysRun = true)
    public void loadExcel() {
        String excelPath = ConfigReader.getProperty("excel.path", "src/test/resources/Login_TC.xlsx");
        excel = new ExcelReader(excelPath);
    }

    @DataProvider(name = "loginCases")
    public Object[][] loginCases() {
        String sheetName = ConfigReader.getProperty("login.sheet", "M2.Login - Encrypt update");
        List<Map<String, String>> testCases = excel.getLoginTestCases(sheetName);

        Object[][] data = new Object[testCases.size()][1];
        for (int i = 0; i < testCases.size(); i++) {
            data[i][0] = testCases.get(i);
        }
        return data;
    }

    @Test(dataProvider = "loginCases")
    public void runLoginTestCase(Map<String, String> testCase) {
        String tcId = testCase.get("TC_ID");
        String description = testCase.getOrDefault("DESCRIPTION", "");
        System.out.println("\n=== RUNNING " + tcId + " - " + description + " ===");

        if (!isSelectedForRun(tcId)) {
            throw new SkipException("Testcase is not selected for this run");
        }

        String skipReason = excel.getAutomationSkipReason(testCase);
        if (!skipReason.isEmpty()) {
            throw new SkipException(skipReason);
        }

        Map<String, String> testData = new LinkedHashMap<>(excel.getTestData(tcId));
        testData.putIfAbsent("base.url", ConfigReader.getProperty("base.url"));

        List<Map<String, String>> steps = excel.getTestStepsForLoginTC(tcId);
        driver = DriverFactory.initDriver();
        engine = new KeywordEngine(driver);
        engine.executeSteps(steps, testData);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownDriver() {
        DriverFactory.quitDriver();
    }

    @AfterClass(alwaysRun = true)
    public void closeExcel() throws IOException {
        if (excel != null) {
            excel.close();
        }
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
}

package runner;

import core.excel.ExcelReader;
import core.engine.KeywordEngine;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.testng.annotations.*;

import java.util.List;
import java.util.Map;

public class TestRunner {

    WebDriver driver;
    ExcelReader excel;
    KeywordEngine engine;

    @BeforeClass
    public void setup() {
        // 👉 Set path nếu cần
        // System.setProperty("webdriver.chrome.driver", "path/to/chromedriver");

        driver = new ChromeDriver();

        // 👉 Đường dẫn file Excel
        excel = new ExcelReader("src/test/resources/testdata.xlsx");

        engine = new KeywordEngine(driver);
    }

    @Test
    public void runAllTestCases() {

        // 👉 Lấy danh sách test case cần chạy
        List<String> testCases = excel.getRunnableTestCases();

        for (String tcId : testCases) {

            System.out.println("===== RUN TESTCASE: " + tcId + " =====");

            // 👉 Lấy data của test case
            Map<String, String> testData = excel.getTestData(tcId);

            // 👉 Lấy steps
            List<Map<String, String>> steps = excel.getTestSteps(tcId);

            // 👉 Execute
            engine.executeSteps(steps, testData);
        }
    }

    @AfterClass
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
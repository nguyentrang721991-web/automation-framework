package utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import core.report.ReportContext;
import core.utils.ConfigReader;
import core.utils.DriverFactory;
import core.utils.ScreenshotUtils;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class ExtentReportListener implements ITestListener {

    private static final Object LOCK = new Object();
    private static final ThreadLocal<ExtentTest> CURRENT_TEST = new ThreadLocal<>();

    private static ExtentReports extent;
    private static String reportPath;

    @Override
    public void onStart(ITestContext context) {
        initReport();
    }

    @Override
    public void onTestStart(ITestResult result) {
        initReport();
        ReportContext.clear();

        ExtentTest test = extent.createTest(testName(result), testDescription(result));
        String featureId = parameterValue(result, "featureId");
        if (!featureId.isBlank()) {
            test.assignCategory(featureId);
        }
        addTestMetadata(test, result);
        CURRENT_TEST.set(test);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentTest test = currentTest(result);
        logSteps(test);
        test.pass(MarkupHelper.createLabel("PASSED", ExtentColor.GREEN));
        cleanup();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ExtentTest test = currentTest(result);
        logSteps(test);
        Throwable throwable = result.getThrowable();
        if (throwable != null) {
            test.fail(throwable);
        }

        if (ConfigReader.getBoolean("screenshot.on.failure", true)) {
            attachFailureScreenshot(test, result);
        }

        test.fail(MarkupHelper.createLabel("FAILED", ExtentColor.RED));
        cleanup();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        ExtentTest test = currentTest(result);
        logSteps(test);
        Throwable throwable = result.getThrowable();
        if (throwable != null) {
            test.skip(throwable.getMessage());
        }
        test.skip(MarkupHelper.createLabel("SKIPPED", ExtentColor.ORANGE));
        cleanup();
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        onTestFailure(result);
    }

    @Override
    public void onFinish(ITestContext context) {
        synchronized (LOCK) {
            if (extent != null) {
                extent.flush();
                System.out.println("Extent report: " + reportPath);
            }
        }
    }

    private static void initReport() {
        synchronized (LOCK) {
            if (extent != null) {
                return;
            }

            try {
                String outputDir = ConfigReader.getProperty("report.output.dir", "reports");
                String fileName = ConfigReader.getProperty("report.file.name", "report.html");
                Path outputPath = Paths.get(outputDir);
                Files.createDirectories(outputPath);
                Files.createDirectories(Paths.get(ConfigReader.getProperty("report.screenshot.dir", "reports/screenshots")));

                reportPath = outputPath.resolve(fileName).toAbsolutePath().toString();
                ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
                spark.config().setDocumentTitle(ConfigReader.getProperty("report.title", "Test Execution Report"));
                spark.config().setReportName(ConfigReader.getProperty("report.name", "Automation Test Report"));
                spark.config().setEncoding("UTF-8");
                spark.config().setTimelineEnabled(ConfigReader.getBoolean("report.timeline.enabled", true));
                spark.config().setTheme(reportTheme());

                extent = new ExtentReports();
                extent.attachReporter(spark);
                extent.setSystemInfo("Environment", ConfigReader.getProperty("env", ""));
                extent.setSystemInfo("Browser", ConfigReader.getProperty("browser", ""));
                extent.setSystemInfo("OS", System.getProperty("os.name"));
                extent.setSystemInfo("Java", System.getProperty("java.version"));
            } catch (Exception e) {
                throw new RuntimeException("Cannot initialize ExtentReports", e);
            }
        }
    }

    private static Theme reportTheme() {
        String value = ConfigReader.getProperty("report.theme", "dark").toLowerCase(Locale.ROOT);
        return "standard".equals(value) || "light".equals(value) ? Theme.STANDARD : Theme.DARK;
    }

    private ExtentTest currentTest(ITestResult result) {
        ExtentTest test = CURRENT_TEST.get();
        if (test == null) {
            initReport();
            test = extent.createTest(testName(result), testDescription(result));
            CURRENT_TEST.set(test);
        }
        return test;
    }

    private void addTestMetadata(ExtentTest test, ITestResult result) {
        String[][] metadata = {
                {"Field", "Value"},
                {"Feature", firstNonBlank(parameterValue(result, "featureId"), parameterValue(result, "featureName"))},
                {"Excel File", parameterValue(result, "excelFile")},
                {"Test Case", parameterValue(result, "testCaseId")}
        };
        test.info(MarkupHelper.createTable(metadata));
    }

    private void logSteps(ExtentTest test) {
        for (ReportContext.StepLog step : ReportContext.stepLogs()) {
            String detail = "<b>" + escapeHtml(step.title()) + "</b><br/><pre>"
                    + escapeHtml(step.detail()) + "</pre>";
            switch (step.status().toUpperCase(Locale.ROOT)) {
                case "PASS":
                    test.pass(detail);
                    break;
                case "FAIL":
                    test.fail(detail);
                    break;
                case "SKIP":
                    test.skip(detail);
                    break;
                default:
                    test.info(detail);
                    break;
            }
        }
    }

    private void attachFailureScreenshot(ExtentTest test, ITestResult result) {
        try {
            WebDriver driver = DriverFactory.getDriver();
            if (driver == null) {
                return;
            }

            String screenshotPath = ScreenshotUtils.capture(driver, testName(result));
            if (screenshotPath != null) {
                test.fail("Failure screenshot",
                        MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
            }
        } catch (Exception e) {
            test.warning("Could not attach screenshot: " + e.getMessage());
        }
    }

    private void cleanup() {
        synchronized (LOCK) {
            if (extent != null) {
                extent.flush();
            }
        }
        ReportContext.remove();
        CURRENT_TEST.remove();
    }

    private String testName(ITestResult result) {
        Object[] parameters = result.getParameters();
        if (parameters != null && parameters.length > 0 && parameters[0] != null) {
            return parameters[0].toString();
        }
        return result.getTestClass().getName() + "." + result.getMethod().getMethodName();
    }

    private String testDescription(ITestResult result) {
        String description = result.getMethod().getDescription();
        return description == null ? "" : description;
    }

    private String parameterValue(ITestResult result, String accessor) {
        Object[] parameters = result.getParameters();
        if (parameters == null || parameters.length == 0 || parameters[0] == null) {
            return "";
        }

        try {
            Method method = parameters[0].getClass().getDeclaredMethod(accessor);
            method.setAccessible(true);
            Object value = method.invoke(parameters[0]);
            if (value instanceof java.util.Map<?, ?> map && "testCaseId".equals(accessor)) {
                Object tcId = map.get("TC_ID");
                return tcId == null ? "" : tcId.toString();
            }
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            if ("testCaseId".equals(accessor)) {
                return testCaseIdFromParameter(parameters[0]);
            }
            return "";
        }
    }

    private String testCaseIdFromParameter(Object parameter) {
        try {
            Method method = parameter.getClass().getDeclaredMethod("testCase");
            method.setAccessible(true);
            Object value = method.invoke(parameter);
            if (value instanceof java.util.Map<?, ?> map) {
                Object tcId = map.get("TC_ID");
                return tcId == null ? "" : tcId.toString();
            }
        } catch (Exception ignored) {
            // Best-effort metadata only.
        }
        return "";
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? nullToEmpty(second) : first;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String value) {
        return nullToEmpty(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

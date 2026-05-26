package utils;

import core.utils.DriverFactory;
import io.qameta.allure.Attachment;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.lang.reflect.Field;

public class AllureListener implements ITestListener {

    @Attachment(value = "Screenshot", type = "image/png")
    public byte[] saveScreenshot(WebDriver driver) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        try {
            WebDriver driver = DriverFactory.getDriver();
            if (driver == null) {
                driver = resolveDriverFromTestInstance(result.getInstance());
            }
            if (driver != null) {
                saveScreenshot(driver);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
    }

    @Override
    public void onTestSuccess(ITestResult result) {
    }

    @Override
    public void onTestSkipped(ITestResult result) {
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    }

    @Override
    public void onStart(ITestContext context) {
    }

    @Override
    public void onFinish(ITestContext context) {
    }

    private WebDriver resolveDriverFromTestInstance(Object testInstance) throws IllegalAccessException {
        Class<?> type = testInstance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("driver");
                field.setAccessible(true);
                Object value = field.get(testInstance);
                return value instanceof WebDriver ? (WebDriver) value : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }
}

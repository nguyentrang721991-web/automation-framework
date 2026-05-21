package utils;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import io.github.bonigarcia.wdm.WebDriverManager;

public class DriverFactory {

    public static WebDriver initDriver() {   // ✅ phải trả về WebDriver
        WebDriverManager.chromedriver().setup();
        return new ChromeDriver();          // ✅ return driver
    }
}
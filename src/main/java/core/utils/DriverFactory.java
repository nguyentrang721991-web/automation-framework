package core.utils;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;

public class DriverFactory {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverFactory() {
    }

    public static WebDriver initDriver() {
        if (DRIVER.get() == null) {
            String browser = ConfigReader.getProperty("browser", "chrome").toLowerCase(Locale.ROOT);
            if (!"chrome".equals(browser)) {
                throw new IllegalArgumentException("Unsupported browser: " + browser);
            }

            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--start-maximized");
            options.addArguments("--disable-notifications");
            options.addArguments("--disable-popup-blocking");

            if (ConfigReader.getBoolean("chrome.profile.enabled", false)) {
                Path profileDir = Paths.get(ConfigReader.getProperty("chrome.profile.dir", "target/chrome-profile"))
                        .toAbsolutePath();
                try {
                    Files.createDirectories(profileDir);
                } catch (Exception e) {
                    throw new RuntimeException("Cannot create Chrome profile directory: " + profileDir, e);
                }
                options.addArguments("--user-data-dir=" + profileDir);
                options.addArguments("--profile-directory=Default");
            }

            if (System.getenv("CI") != null || ConfigReader.getBoolean("headless", false)) {
                options.addArguments("--headless=new");
                options.addArguments("--window-size=1440,1000");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
            }

            WebDriver webDriver = new ChromeDriver(options);
            webDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(ConfigReader.getInt("implicit.wait", 0)));
            DRIVER.set(webDriver);
        }
        return DRIVER.get();
    }

    public static WebDriver getDriver() {
        return DRIVER.get();
    }

    public static void quitDriver() {
        if (DRIVER.get() != null) {
            DRIVER.get().quit();
            DRIVER.remove();
        }
    }
}

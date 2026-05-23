package core.keywords;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import io.github.bonigarcia.wdm.WebDriverManager;

public class Actions {

    public WebDriver driver;

    // =========================
    // OPEN BROWSER
    // =========================
    public void openBrowser(String browser) {

        if (browser.equalsIgnoreCase("chrome")) {
            WebDriverManager.chromedriver().setup();
            driver = new ChromeDriver();
        }

        driver.manage().window().maximize();
    }

    // =========================
    // NAVIGATE
    // =========================
    public void navigate(String url) {
        driver.get(url);
    }

    // =========================
    // CLICK
    // =========================
    public void click(By by) {
        driver.findElement(by).click();
    }

    // =========================
    // INPUT
    // =========================
    public void input(By by, String value) {
        driver.findElement(by).clear();
        driver.findElement(by).sendKeys(value);
    }
}
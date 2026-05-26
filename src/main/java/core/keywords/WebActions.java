package core.keywords;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;
import java.util.List;

public class WebActions {

    private WebDriver driver;
    private WebDriverWait wait;

    private final int DEFAULT_TIMEOUT = 10;

    public WebActions(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT));
    }

    // ==============================
    // 🔍 ELEMENT HANDLING
    // ==============================

    public WebElement findElement(By locator) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public List<WebElement> findElements(By locator) {
        return driver.findElements(locator);
    }

    public boolean isElementDisplayed(By locator) {
        try {
            return findElement(locator).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isElementEnabled(By locator) {
        return findElement(locator).isEnabled();
    }

    // ==============================
    // ⌨️ ACTIONS
    // ==============================

    public void click(By locator) {
        waitForElementClickable(locator);
        findElement(locator).click();
    }

    public void clickJS(By locator) {
        WebElement element = findElement(locator);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].click();", element);
    }

    public void sendKeys(By locator, String value) {
        waitForElementVisible(locator);
        WebElement element = findElement(locator);
        element.clear();
        element.sendKeys(value);
    }

    public void clear(By locator) {
        findElement(locator).clear();
    }

    // ==============================
    // ⏳ WAIT
    // ==============================

    public void waitForElementVisible(By locator) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public void waitForElementClickable(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    public void waitForElementInvisible(By locator) {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public void waitForTextToBe(By locator, String text) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(locator, text));
    }

    public void waitForPageLoaded() {
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(
                webDriver -> ((JavascriptExecutor) webDriver)
                        .executeScript("return document.readyState").equals("complete")
        );
    }

    // ==============================
    // 📄 DROPDOWN
    // ==============================

    public void selectByVisibleText(By locator, String text) {
        Select select = new Select(findElement(locator));
        select.selectByVisibleText(text);
    }

    public void selectByValue(By locator, String value) {
        Select select = new Select(findElement(locator));
        select.selectByValue(value);
    }

    // ==============================
    // 🧪 VALIDATION
    // ==============================

    public String getText(By locator) {
        return findElement(locator).getText();
    }

    public String getAttribute(By locator, String attr) {
        return findElement(locator).getAttribute(attr);
    }

    public boolean verifyTextEquals(By locator, String expected) {
        return getText(locator).equals(expected);
    }

    // ==============================
    // 🧭 WINDOW
    // ==============================

    public void switchToNewTab() {
        for (String handle : driver.getWindowHandles()) {
            driver.switchTo().window(handle);
        }
    }

    public void switchToWindow(String title) {
        for (String handle : driver.getWindowHandles()) {
            driver.switchTo().window(handle);
            if (driver.getTitle().contains(title)) {
                return;
            }
        }
    }

    // ==============================
    // 🔄 ALERT
    // ==============================

    public void acceptAlert() {
        wait.until(ExpectedConditions.alertIsPresent());
        driver.switchTo().alert().accept();
    }

    public void dismissAlert() {
        wait.until(ExpectedConditions.alertIsPresent());
        driver.switchTo().alert().dismiss();
    }

    public String getAlertText() {
        wait.until(ExpectedConditions.alertIsPresent());
        return driver.switchTo().alert().getText();
    }

    // ==============================
    // 🧩 ADVANCED ACTIONS
    // ==============================

    public void hoverToElement(By locator) {
        Actions actions = new Actions(driver);
        actions.moveToElement(findElement(locator)).perform();
    }

    public void doubleClick(By locator) {
        Actions actions = new Actions(driver);
        actions.doubleClick(findElement(locator)).perform();
    }

    public void rightClick(By locator) {
        Actions actions = new Actions(driver);
        actions.contextClick(findElement(locator)).perform();
    }

    // ==============================
    // 📂 FRAME
    // ==============================

    public void switchToFrame(By locator) {
        driver.switchTo().frame(findElement(locator));
    }

    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
    }

    // ==============================
    // ⚙️ UTIL
    // ==============================

    public void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
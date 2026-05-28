package core.keywords;

import core.utils.ConfigReader;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Actions {

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String rootWindow;
    private Set<String> previousWindows;

    public Actions(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigReader.getInt("explicit.wait", 20)));
        this.rootWindow = safeWindowHandle();
        this.previousWindows = new HashSet<>(safeWindowHandles());
    }

    public void navigate(String url) {
        if (isBlank(url)) {
            throw new IllegalArgumentException("OPEN_URL requires URL from TestSteps/Input and TestData");
        }
        previousWindows = new HashSet<>(safeWindowHandles());
        driver.get(url);
    }

    public void click(By by) {
        wait.until(ExpectedConditions.elementToBeClickable(by)).click();
    }

    public boolean clickIfVisible(By by, int timeoutSeconds) {
        WebElement element = findFirstVisible(timeoutSeconds, by);
        if (element == null || !element.isEnabled()) {
            return false;
        }
        return clickElement(element);
    }

    public boolean clickIfInputPresent(By by, String input, int timeoutSeconds) {
        if (isBlank(input)) {
            return false;
        }
        return clickIfVisible(by, timeoutSeconds);
    }

    public void clickDeep(By by, int timeoutSeconds) {
        WebElement element = findFirstVisibleAcrossWindows(timeoutSeconds, by);
        if (element == null) {
            throw new RuntimeException("Cannot find element to click: " + by);
        }
        scrollIntoView(element);
        if (!clickElementDeep(element)) {
            throw new RuntimeException("Cannot click element: " + by);
        }
    }

    public void input(By by, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
        setElementValue(element, value);
    }

    public boolean inputIfVisible(By by, String value, int timeoutSeconds) {
        if (isBlank(value)) {
            return false;
        }
        WebElement element = findFirstVisibleAcrossWindows(timeoutSeconds, by);
        if (element == null) {
            return false;
        }
        setElementValue(element, value);
        return true;
    }

    public void inputEditor(By by, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
        typeIntoEditor(element, value);
    }

    public void uploadFile(By fileInput, String filePath) {
        if (isBlank(filePath)) {
            throw new IllegalArgumentException("UPLOAD_FILE requires file path from TestSteps/Input and TestData");
        }

        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        }
        if (!Files.exists(path)) {
            throw new RuntimeException("Upload file does not exist: " + path);
        }

        WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(fileInput));
        makeFileInputInteractable(input);
        input.sendKeys(path.toString());
    }

    public void waitForSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }

    public void waitUntilVisible(By by, int timeoutSeconds) {
        WebElement element = findFirstVisibleAcrossWindows(timeoutSeconds, by);
        if (element == null) {
            throw new TimeoutException("Element was not visible within " + timeoutSeconds + " seconds: " + by);
        }
    }

    public boolean waitUntilNotVisible(By by, int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(currentDriver -> currentDriver.findElements(by).stream().noneMatch(WebElement::isDisplayed));
        } catch (Exception ignored) {
            return false;
        }
    }

    public void switchToNewWindowIfOpened(int timeoutSeconds) {
        Set<String> before = previousWindows.isEmpty() ? Set.of(safeWindowHandle()) : previousWindows;
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            shortWait.until(currentDriver -> currentDriver.getWindowHandles().size() > before.size());
            for (String window : safeWindowHandles()) {
                if (!before.contains(window)) {
                    driver.switchTo().window(window);
                    previousWindows = new HashSet<>(safeWindowHandles());
                    return;
                }
            }
        } catch (Exception ignored) {
            // Some auth providers reuse the current tab.
        }
        previousWindows = new HashSet<>(safeWindowHandles());
    }

    public void switchToWindowWithVisibleElement(By by, int timeoutSeconds) {
        WebElement element = findFirstVisibleAcrossWindows(timeoutSeconds, by);
        if (element == null) {
            throw new TimeoutException("Cannot find visible element in any window: " + by);
        }
    }

    public void switchToRootWindow() {
        if (!isBlank(rootWindow) && safeWindowHandles().contains(rootWindow)) {
            driver.switchTo().window(rootWindow);
        }
    }

    public void assertElementVisible(By by) {
        waitUntilVisible(by, ConfigReader.getInt("assert.wait", 30));
    }

    public void assertUrlContains(String expectedPart) {
        if (!wait.until(currentDriver -> currentDriver.getCurrentUrl().contains(expectedPart))) {
            throw new AssertionError("Current URL does not contain: " + expectedPart + ". Actual: " + currentUrl());
        }
    }

    public void assertUrlEquals(String expectedUrl) {
        if (!wait.until(currentDriver -> currentDriver.getCurrentUrl().equals(expectedUrl))) {
            throw new AssertionError("Current URL does not equal: " + expectedUrl + ". Actual: " + currentUrl());
        }
    }

    public void assertTitleContains(String expectedPart) {
        if (!wait.until(currentDriver -> currentDriver.getTitle().contains(expectedPart))) {
            throw new AssertionError("Page title does not contain: " + expectedPart + ". Actual: " + driver.getTitle());
        }
    }

    public void assertTitleEquals(String expectedTitle) {
        if (!wait.until(currentDriver -> currentDriver.getTitle().equals(expectedTitle))) {
            throw new AssertionError("Page title does not equal: " + expectedTitle + ". Actual: " + driver.getTitle());
        }
    }

    public void assertAnyText(String expectedTexts) {
        List<String> candidates = Arrays.stream(nullToEmpty(expectedTexts).split("\\|"))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Expected text is required");
        }

        WebDriverWait assertWait = new WebDriverWait(driver, Duration.ofSeconds(ConfigReader.getInt("assert.wait", 30)));
        try {
            assertWait.until(currentDriver -> {
                String actualText = normalizeForAssertion(getVisibleText() + " " + currentDriver.getPageSource());
                return candidates.stream()
                        .map(this::normalizeForAssertion)
                        .anyMatch(actualText::contains);
            });
        } catch (TimeoutException e) {
            throw new AssertionError("Cannot find any expected text: " + expectedTexts
                    + ". Current URL: " + currentUrl()
                    + ". Page text sample: " + textSample(getVisibleText()), e);
        }
    }

    public void assertTextContains(By by, String expectedText) {
        assertText(by, expectedText, "CONTAINS");
    }

    public void assertText(By by, String expectedText, String assertion) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
        String actual = element.getText();
        String normalizedActual = normalizeForAssertion(actual);
        List<String> candidates = Arrays.stream(nullToEmpty(expectedText).split("\\|"))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Expected text is required");
        }

        boolean equalsAssertion = assertion != null
                && List.of("equals", "equal", "eq", "=").contains(assertion.trim().toLowerCase(Locale.ROOT));
        boolean matched = candidates.stream()
                .map(this::normalizeForAssertion)
                .anyMatch(candidate -> equalsAssertion ? normalizedActual.equals(candidate) : normalizedActual.contains(candidate));

        if (!matched) {
            String operator = equalsAssertion ? "equal" : "contain";
            throw new AssertionError("Element text does not " + operator + " '" + expectedText + "'. Actual: " + actual);
        }
    }

    private void setElementValue(WebElement element, String value) {
        try {
            element.clear();
            element.sendKeys(nullToEmpty(value));
        } catch (Exception ignored) {
            typeIntoEditor(element, nullToEmpty(value));
        }
    }

    private WebElement findFirstVisible(int timeoutSeconds, By... locators) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(currentDriver -> {
                for (By locator : locators) {
                    for (WebElement element : currentDriver.findElements(locator)) {
                        if (isDisplayed(element)) {
                            return element;
                        }
                    }
                }
                return null;
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private WebElement findFirstVisibleAcrossWindows(int timeoutSeconds, By... locators) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        String originalWindow = safeWindowHandle();
        while (System.currentTimeMillis() < deadline) {
            for (String window : safeWindowHandles()) {
                try {
                    driver.switchTo().window(window);
                    WebElement element = findFirstVisible(1, locators);
                    if (element != null) {
                        return element;
                    }
                } catch (Exception ignored) {
                    // Windows can close while an auth provider redirects.
                }
            }
            sleepMillis(250);
        }
        if (!isBlank(originalWindow) && safeWindowHandles().contains(originalWindow)) {
            driver.switchTo().window(originalWindow);
        }
        return null;
    }

    private boolean clickElement(WebElement element) {
        try {
            element.click();
            return true;
        } catch (Exception ignored) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                return true;
            } catch (Exception ignoredAgain) {
                return false;
            }
        }
    }

    private boolean clickElementDeep(WebElement element) {
        if (clickElement(element)) {
            return true;
        }

        try {
            new org.openqa.selenium.interactions.Actions(driver).moveToElement(element).click().perform();
            return true;
        } catch (Exception ignored) {
            try {
                ((JavascriptExecutor) driver).executeScript(
                        "const r=arguments[0].getBoundingClientRect();"
                                + "const x=r.left+r.width/2; const y=r.top+r.height/2;"
                                + "for (const t of ['pointerdown','mousedown','pointerup','mouseup','click']) {"
                                + "  arguments[0].dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,clientX:x,clientY:y}));"
                                + "}",
                        element);
                return true;
            } catch (Exception ignoredAgain) {
                return false;
            }
        }
    }

    private void makeFileInputInteractable(WebElement input) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].style.display='block';"
                            + "arguments[0].style.visibility='visible';"
                            + "arguments[0].style.opacity='1';"
                            + "arguments[0].style.height='1px';"
                            + "arguments[0].style.width='1px';"
                            + "arguments[0].value='';",
                    input);
        } catch (Exception ignored) {
            // Browser security rules can reject style/value changes on file inputs.
        }
    }

    private void scrollIntoView(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
        } catch (Exception ignored) {
            // Best-effort helper before clicking dynamic elements.
        }
    }

    private void typeIntoEditor(WebElement element, String value) {
        element.click();
        WebElement activeElement = driver.switchTo().activeElement();
        activeElement.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        activeElement.sendKeys(nullToEmpty(value));
    }

    private Set<String> safeWindowHandles() {
        try {
            return driver.getWindowHandles();
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private String safeWindowHandle() {
        try {
            return driver.getWindowHandle();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isDisplayed(WebElement element) {
        try {
            return element != null && element.isDisplayed();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void sleepMillis(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }

    private String getVisibleText() {
        try {
            Object text = ((JavascriptExecutor) driver).executeScript("return document.body ? document.body.innerText : '';");
            return text == null ? "" : text.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String currentUrl() {
        try {
            return driver.getCurrentUrl();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String textSample(String text) {
        String compact = nullToEmpty(text).replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeForAssertion(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}

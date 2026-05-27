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

import java.io.Console;
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

    public Actions(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigReader.getInt("explicit.wait", 20)));
    }

    public void navigate(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("OPEN_URL requires URL from TestSteps/Input and TestData");
        }
        driver.get(url);
    }

    public void click(By by) {
        wait.until(ExpectedConditions.elementToBeClickable(by)).click();
    }

    public boolean clickIfVisible(By by, int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            shortWait.until(ExpectedConditions.elementToBeClickable(by)).click();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void input(By by, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
        try {
            element.clear();
            element.sendKeys(value == null ? "" : value);
        } catch (Exception ignored) {
            typeIntoEditor(element, value == null ? "" : value);
        }
    }

    public void inputEditor(By by, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
        typeIntoEditor(element, value == null ? "" : value);
    }

    public void clickDeep(By by, int timeoutSeconds) {
        WebElement element = findFirstVisibleAcross(timeoutSeconds, by);
        if (element == null) {
            throw new RuntimeException("Cannot find element to click: " + by);
        }
        scrollIntoView(element);
        if (!clickElementDeep(element)) {
            throw new RuntimeException("Cannot click element: " + by);
        }
    }

    public void uploadFile(By triggerOrInput, String filePath) {
        if (isBlank(filePath)) {
            throw new IllegalArgumentException("File path is required for uploadfile keyword");
        }

        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        }
        if (!Files.exists(path)) {
            throw new RuntimeException("Upload file does not exist: " + path);
        }

        WebElement input = findUploadInput(triggerOrInput, 5);
        if (input == null) {
            clickIfVisible(triggerOrInput, 5);
            input = findUploadInput(triggerOrInput, 10);
        }
        if (input == null) {
            throw new RuntimeException("Cannot find file input for upload. Trigger: " + triggerOrInput
                    + ". Current URL: " + currentUrl()
                    + ". Page text sample: " + textSample(getVisibleText()));
        }

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
            // Some browsers only allow clearing file input through sendKeys replacement.
        }

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

    private void waitForMilliseconds(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }

    public void loginWithMicrosoft(String email, String password) {
        if (isBlank(email) || isBlank(password)) {
            throw new IllegalArgumentException("Microsoft login requires email and password in TestData");
        }

        try {
            waitForPageStable(2);
            if (isAuthenticatedOrAppReady()) {
                return;
            }

            String mainWindow = driver.getWindowHandle();
            Set<String> handlesBeforeClick = new HashSet<>(driver.getWindowHandles());

            boolean clicked = clickMicrosoftSsoButton();
            if (!clicked) {
                throw new RuntimeException("Cannot find Microsoft SSO login button. Current URL: " + currentUrl()
                        + ". Page text sample: " + textSample(getVisibleText()));
            }

            switchToNewWindowIfOpened(mainWindow, handlesBeforeClick);
            submitMicrosoftCredentials(email, password);
            handleStaySignedInPrompt(mainWindow);
            switchBackIfPossible(mainWindow);
        } catch (Exception e) {
            throw new RuntimeException("Microsoft SSO failed: " + e.getMessage(), e);
        }
    }

    private boolean clickMicrosoftSsoButton() {
        By[] locators = new By[]{
                By.cssSelector("button.login-sso-button"),
                By.cssSelector("button[class*='sso' i], a[class*='sso' i]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'Microsoft')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'SSO')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'\u0110\u0103ng nh\u1eadp')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'Sign in') or contains(normalize-space(.),'Login')]")
        };

        WebElement button = findFirstVisibleAcross(ConfigReader.getInt("sso.button.wait", 20), locators);
        if (button != null) {
            scrollIntoView(button);
            return clickElementDeep(button);
        }

        return clickMicrosoftSsoButtonByScript(ConfigReader.getInt("sso.button.wait", 20));
    }

    private boolean clickMicrosoftSsoButtonByScript(int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "const visible = (el) => {"
                            + " if (!el) return false;"
                            + " const style = window.getComputedStyle(el);"
                            + " const rect = el.getBoundingClientRect();"
                            + " return style.display !== 'none' && style.visibility !== 'hidden' "
                            + "   && Number(style.opacity || 1) > 0 && rect.width > 0 && rect.height > 0;"
                            + "};"
                            + "const candidates = [...document.querySelectorAll('button,a,[role=\"button\"]')].filter(visible);"
                            + "const target = candidates.find(el => /microsoft|sso|sign in|login|dang nhap|đăng nhập/i.test(el.innerText || el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || ''));"
                            + "if (!target) return false;"
                            + "target.scrollIntoView({block:'center', inline:'center'});"
                            + "target.click();"
                            + "return true;"
            ));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void switchToNewWindowIfOpened(String mainWindow, Set<String> handlesBeforeClick) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
            shortWait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
            for (String window : driver.getWindowHandles()) {
                if (!handlesBeforeClick.contains(window)) {
                    driver.switchTo().window(window);
                    return;
                }
            }
        } catch (TimeoutException ignored) {
            driver.switchTo().window(mainWindow);
        }
    }

    private void submitMicrosoftCredentials(String email, String password) {
        By emailInput = By.id("i0116");
        By passwordInput = By.id("i0118");
        By nextButton = By.id("idSIButton9");

        if (isVisible(emailInput, 20)) {
            input(emailInput, email);
            click(nextButton);
        }

        if (isVisible(passwordInput, 20)) {
            input(passwordInput, password);
            click(nextButton);
        }
    }

    private boolean handleStaySignedInPrompt(String returnWindow) {
        long deadline = System.currentTimeMillis() + ConfigReader.getInt("stay.signed.in.wait", 12) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            for (String window : driver.getWindowHandles()) {
                try {
                    driver.switchTo().window(window);
                    if (clickStaySignedInYesIfPresent() || clickStaySignedInYesByScript()) {
                        switchBackIfPossible(returnWindow);
                        return true;
                    }
                } catch (Exception ignored) {
                    // Microsoft auth windows can close during redirects.
                }
            }
            waitForMilliseconds(500);
        }

        switchBackIfPossible(returnWindow);
        return false;
    }

    private boolean clickStaySignedInYesIfPresent() {
        List<By> yesLocators = List.of(
                By.id("acceptButton"),
                By.xpath("//input[@type='submit' and (normalize-space(@value)='Yes' or normalize-space(@value)='C\u00f3')]"),
                By.xpath("//button[normalize-space(.)='Yes' or normalize-space(.)='C\u00f3']"),
                By.xpath("//*[@role='button' and (normalize-space(.)='Yes' or normalize-space(.)='C\u00f3')]"),
                By.xpath("//*[self::button or self::input or @role='button'][contains(@aria-label,'Yes') or contains(@aria-label,'C\u00f3')]"),
                By.xpath("//*[@data-testid='primaryButton' or @data-test-id='primaryButton']"),
                By.id("idSIButton9")
        );

        boolean promptVisible = pageContainsAny(
                "Stay signed in?",
                "Stay signed in",
                "Do this to reduce the number of times you are asked to sign in",
                "Duy tr\u00ec \u0111\u0103ng nh\u1eadp",
                "Gi\u1eef \u0111\u0103ng nh\u1eadp"
        );

        boolean hasCandidateButton = yesLocators.stream().anyMatch(locator -> {
            try {
                return !driver.findElements(locator).isEmpty();
            } catch (Exception ignored) {
                return false;
            }
        });

        if (!promptVisible && !hasCandidateButton) {
            return false;
        }

        for (By locator : yesLocators) {
            try {
                for (WebElement element : driver.findElements(locator)) {
                    if (element.isDisplayed() && element.isEnabled() && clickElement(element)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // Try the next candidate.
            }
        }
        return false;
    }

    private boolean clickStaySignedInYesByScript() {
        try {
            return (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "const visible = (el) => {"
                            + " if (!el) return false;"
                            + " const style = window.getComputedStyle(el);"
                            + " const rect = el.getBoundingClientRect();"
                            + " return style.display !== 'none' && style.visibility !== 'hidden' "
                            + "   && Number(style.opacity || 1) > 0 && rect.width > 0 && rect.height > 0;"
                            + "};"
                            + "const bodyText = (document.body && document.body.innerText || '').toLowerCase();"
                            + "if (!bodyText.includes('stay signed in') && !bodyText.includes('duy tri dang nhap') && !bodyText.includes('giữ đăng nhập')) return false;"
                            + "const candidates = [...document.querySelectorAll('input,button,[role=\"button\"]')].filter(visible);"
                            + "const target = candidates.find(el => {"
                            + " const text = ((el.value || el.innerText || el.textContent || el.getAttribute('aria-label') || el.id || '') + '').trim().toLowerCase();"
                            + " return el.id === 'idSIButton9' || el.id === 'acceptButton' || text === 'yes' || text === 'có' || text.includes('yes') || text.includes('có');"
                            + "});"
                            + "if (!target) return false;"
                            + "target.scrollIntoView({block:'center', inline:'center'});"
                            + "target.click();"
                            + "return true;"
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    private void switchBackIfPossible(String mainWindow) {
        try {
            if (driver.getWindowHandles().contains(mainWindow)) {
                driver.switchTo().window(mainWindow);
            }
        } catch (Exception ignored) {
            // The Microsoft window can close during redirect.
        }
    }

    public void enterMicrosoftOtp(String otp) {
        By otpInput = By.xpath("//input[contains(@aria-label,'Code') or contains(@placeholder,'Code') "
                + "or @id='idTxtBx_SAOTCC_TD' or contains(@name,'otc') or @type='tel']");

        String originalWindow = driver.getWindowHandle();
        if (!switchToWindowWithVisibleElement(otpInput, 5)) {
            waitForAuthenticationToCompleteOrFail(originalWindow, ConfigReader.getInt("auth.wait", 90));
            return;
        }

        String resolvedOtp = resolveSecret(otp, "microsoftOtp", "MICROSOFT_OTP");
        if (isBlank(resolvedOtp)) {
            waitForManualMicrosoftMfa(otpInput, originalWindow, ConfigReader.getInt("manual.mfa.timeout", 180));
            return;
        }

        input(otpInput, resolvedOtp);
        clickFirstVisible(5,
                By.id("idSubmit_SAOTCC_Continue"),
                By.xpath("//input[@value='Verify' or @value='X\u00e1c minh']"),
                By.xpath("//*[self::button or @role='button'][contains(normalize-space(.),'Verify') or contains(normalize-space(.),'X\u00e1c minh')]")
        );
        handleStaySignedInPrompt(originalWindow);
        switchBackIfPossible(originalWindow);
        waitForAuthenticationToCompleteOrFail(originalWindow, ConfigReader.getInt("auth.wait", 90));
    }

    private void waitForManualMicrosoftMfa(By otpInput, String returnWindow, int timeoutSeconds) {
        System.out.printf("Microsoft MFA is displayed but microsoftOtp is not provided. Waiting up to %d seconds for manual MFA completion.%n",
                timeoutSeconds);
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            handleStaySignedInPrompt(returnWindow);
            switchBackIfPossible(returnWindow);
            if (isAuthenticatedOrAppReady()) {
                return;
            }
            if (!switchToWindowWithVisibleElement(otpInput, 1)) {
                if (waitForAuthenticationToComplete(returnWindow, 10)) {
                    return;
                }
            }
            waitForMilliseconds(500);
        }
        throw new RuntimeException("Microsoft MFA is displayed but microsoftOtp is not provided and manual MFA did not complete within "
                + timeoutSeconds + " seconds");
    }

    private void waitForAuthenticationToCompleteOrFail(String returnWindow, int timeoutSeconds) {
        if (waitForAuthenticationToComplete(returnWindow, timeoutSeconds)) {
            return;
        }

        throw new RuntimeException("Authentication did not complete within " + timeoutSeconds
                + " seconds. Current URL: " + currentUrl()
                + ". Page text sample: " + textSample(getVisibleText()));
    }

    private boolean waitForAuthenticationToComplete(String returnWindow, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            handleStaySignedInPrompt(returnWindow);
            switchBackIfPossible(returnWindow);
            if (isAuthenticatedOrAppReady()) {
                return true;
            }
            waitForMilliseconds(500);
        }

        return false;
    }

    public void enterAppPin(String pin) {
        String resolvedPin = resolveSecret(pin, "appPin", "APP_PIN");
        if (isBlank(resolvedPin)) {
            throw new RuntimeException("App PIN/passphase is required but appPin is not provided");
        }

        By pinInput = By.xpath("//input[@type='password' or @type='tel' "
                + "or contains(@placeholder,'PIN') or contains(@aria-label,'PIN') "
                + "or contains(@placeholder,'m\u00e3') or contains(@aria-label,'m\u00e3')]");
        input(pinInput, resolvedPin);
        clickFirstVisible(5,
                By.xpath("//*[self::button or @role='button'][contains(normalize-space(.),'Ti\u1ebfp t\u1ee5c')]"),
                By.xpath("//*[self::button or @role='button'][contains(normalize-space(.),'\u0110\u0103ng nh\u1eadp')]"),
                By.xpath("//*[self::button or @role='button'][contains(normalize-space(.),'X\u00e1c nh\u1eadn')]"),
                By.xpath("//*[self::button or @role='button'][contains(normalize-space(.),'Continue') or contains(normalize-space(.),'Login')]")
        );
    }

    public boolean skipSecurityKeySetup() {
        boolean clicked = clickFirstVisible(10,
                By.xpath("//button[.//span[normalize-space(.)='B\u1ecf qua'] or normalize-space(.)='B\u1ecf qua']"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'B\u1ecf qua')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'Skip')]")
        );

        if (!clicked) {
            clicked = clickSkipSecurityKeySetupByScript(5);
        }

        if (clicked) {
            waitUntilNotSecurityKeySetup(ConfigReader.getInt("app.ready.wait", 30));
            return true;
        }

        if (!isSecurityKeySetupPage() && isAppReady()) {
            return true;
        }

        throw new RuntimeException("Cannot find security key setup skip button and app is not ready. Current URL: "
                + currentUrl() + ". Page text sample: " + textSample(getVisibleText()));
    }

    private boolean clickSkipSecurityKeySetupByScript(int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "const visible = (el) => {"
                            + " if (!el) return false;"
                            + " const style = window.getComputedStyle(el);"
                            + " const rect = el.getBoundingClientRect();"
                            + " return style.display !== 'none' && style.visibility !== 'hidden'"
                            + "   && Number(style.opacity || 1) > 0 && rect.width > 0 && rect.height > 0;"
                            + "};"
                            + "const candidates = [...document.querySelectorAll('button,a,[role=\"button\"]')].filter(visible);"
                            + "const target = candidates.find(el => {"
                            + " const text = (el.innerText || el.textContent || '').trim().toLowerCase();"
                            + " return text === 'bỏ qua' || text === 'bo qua' || text.includes('skip');"
                            + "});"
                            + "if (!target) return false;"
                            + "target.scrollIntoView({block:'center', inline:'center'});"
                            + "target.click();"
                            + "return true;"
            ));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean waitUntilNotSecurityKeySetup(int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(driver -> !isSecurityKeySetupPage() && isAppReady());
        } catch (Exception ignored) {
            return false;
        }
    }

    public void openCreateGroupChat() {
        ensureAppReadyBeforeAction("open create group chat");

        if (clickCreateGroupChatByScript()) {
            if (waitForCreateGroupChatOpened(8)) {
                return;
            }
        }

        By[] candidateLocators = new By[]{
                By.cssSelector("div.create-room.cursor-pointer"),
                By.cssSelector(".create-room"),
                By.xpath("//div[contains(concat(' ',normalize-space(@class),' '),' create-room ')]"),
                By.xpath("//*[self::button or self::a or @role='button' or contains(@class,'cursor-pointer')]"
                        + "[contains(@aria-label,'Create new group') or contains(@title,'Create new group')]"),
                By.xpath("//*[self::button or self::a or @role='button' or contains(@class,'cursor-pointer')]"
                        + "[contains(@aria-label,'New group') or contains(@title,'New group')]"),
                By.xpath("//*[self::button or self::a or @role='button' or contains(@class,'cursor-pointer')]"
                        + "[contains(@aria-label,'T\u1ea1o nh\u00f3m') or contains(@title,'T\u1ea1o nh\u00f3m')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(@aria-label,'T\u1ea1o') and contains(@aria-label,'nh\u00f3m')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(@title,'T\u1ea1o') and contains(@title,'nh\u00f3m')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'T\u1ea1o nh\u00f3m')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(@aria-label,'Create') and contains(@aria-label,'group')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(@title,'Create') and contains(@title,'group')]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'Create group') or contains(normalize-space(.),'New group')]"),
                By.xpath("//div[contains(@class,'tippy') or contains(@class,'tooltip') or @role='tooltip']"
                        + "[contains(normalize-space(.),'Create new group')]"
                        + "/preceding::*[self::button or self::a or @role='button' or contains(@class,'cursor-pointer')][1]"),
                By.xpath("//*[local-name()='svg'][.//*[local-name()='rect' and @width='34' and @height='34' and @rx='17']]"
                        + "/ancestor::*[self::button or self::a or @role='button' or contains(@class,'cursor-pointer')][1]"),
                By.xpath("//*[local-name()='rect' and @width='34' and @height='34' and @rx='17']"
                        + "/ancestor::*[self::button or self::a or @role='button' or contains(@class,'cursor-pointer')][1]"),
                By.xpath("//*[local-name()='rect' and @width='34' and @height='34' and @rx='17']"
                        + "/ancestor::*[local-name()='svg'][1]"),
                By.xpath("//*[local-name()='rect' and @width='34' and @height='34' and @rx='17']")
        };

        WebElement candidate = findFirstVisibleAcross(ConfigReader.getInt("create.group.wait", 12), candidateLocators);
        if (candidate != null) {
            scrollIntoView(candidate);
            hover(candidate);
            if (clickElementDeep(candidate) && waitForCreateGroupChatOpened(8)) {
                return;
            }
        }

        throw new RuntimeException("Cannot open create group chat screen. Current URL: " + currentUrl()
                + ". Page text sample: " + textSample(getVisibleText()));
    }

    public void clickSecurityLockToggle() {
        clickFirstElement("Cannot find security lock toggle", 10,
                By.cssSelector("span.slider.round"),
                By.xpath("//span[contains(concat(' ',normalize-space(@class),' '),' slider ') "
                        + "and contains(concat(' ',normalize-space(@class),' '),' round ')]"),
                By.xpath("//span[contains(concat(' ',normalize-space(@class),' '),' slider ') "
                        + "and contains(concat(' ',normalize-space(@class),' '),' round ')]"
                        + "/ancestor::*[self::label or self::button or @role='switch' or contains(@class,'switch') or contains(@class,'toggle')][1]"),
                By.xpath("//*[self::button or @role='switch' or @role='button'][contains(normalize-space(.),'B\u1ea3o m\u1eadt') or contains(normalize-space(.),'Security')]")
        );
    }

    public void searchAndOpenAccount(String account) {
        if (isBlank(account)) {
            throw new IllegalArgumentException("Account value is required");
        }

        ensureAppReadyBeforeAction("search account");

        WebElement searchInput = findFirstVisibleAcross(5,
                By.cssSelector("p[data-placeholder='Search']"),
                By.xpath("//*[@data-placeholder='Search']"),
                By.xpath("//*[@contenteditable='true'][.//*[@data-placeholder='Search'] or @data-placeholder='Search']"),
                By.cssSelector("input[placeholder*='Search' i]"),
                By.cssSelector("input[aria-label*='Search' i]"),
                By.cssSelector("input[placeholder*='T\u00ecm' i]"),
                By.cssSelector("input[aria-label*='T\u00ecm' i]"),
                By.xpath("//input[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'search') "
                        + "or contains(translate(@aria-label,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'search') "
                        + "or contains(@placeholder,'T\u00ecm') or contains(@aria-label,'T\u00ecm')]")
        );

        if (searchInput == null) {
            clickFirstVisible(5,
                    By.xpath("//*[self::button or @role='button'][contains(@aria-label,'Search') or contains(@title,'Search') or contains(@aria-label,'T\u00ecm') or contains(@title,'T\u00ecm')]"),
                    By.xpath("//*[self::button or @role='button'][.//*[local-name()='svg'] and (contains(normalize-space(.),'Search') or contains(normalize-space(.),'T\u00ecm'))]")
            );
            searchInput = findFirstVisibleAcross(5,
                    By.cssSelector("p[data-placeholder='Search']"),
                    By.xpath("//*[@data-placeholder='Search']"),
                    By.xpath("//*[@contenteditable='true'][.//*[@data-placeholder='Search'] or @data-placeholder='Search']"),
                    By.cssSelector("input[placeholder*='Search' i]"),
                    By.cssSelector("input[aria-label*='Search' i]"),
                    By.cssSelector("input[placeholder*='T\u00ecm' i]"),
                    By.cssSelector("input[aria-label*='T\u00ecm' i]"),
                    By.xpath("//input[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'search') "
                            + "or contains(translate(@aria-label,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'search') "
                            + "or contains(@placeholder,'T\u00ecm') or contains(@aria-label,'T\u00ecm')]")
            );
        }

        if (searchInput == null) {
            throw new RuntimeException("Cannot find account search input. Current URL: " + currentUrl()
                    + ". Page text sample: " + textSample(getVisibleText()));
        }

        typeIntoEditor(searchInput, account);

        clickAccountSearchResult(account, searchInput);
    }

    public void sendChatMessage(String message) {
        if (isBlank(message)) {
            throw new IllegalArgumentException("Message value is required");
        }

        ensureAppReadyBeforeAction("send chat message");

        WebElement messageBox = findFirstVisible(15,
                By.cssSelector("p[data-placeholder='Message']"),
                By.xpath("//*[@data-placeholder='Message']"),
                By.xpath("//*[@contenteditable='true'][.//*[@data-placeholder='Message'] or @data-placeholder='Message']"),
                By.cssSelector("textarea[placeholder*='message' i]"),
                By.cssSelector("input[placeholder*='message' i]"),
                By.cssSelector("[contenteditable='true']"),
                By.xpath("//*[@role='textbox' or self::textarea or self::input]"
                        + "[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'message') "
                        + "or contains(@placeholder,'Tin nh\u1eafn') or contains(@aria-label,'Tin nh\u1eafn') "
                        + "or contains(@aria-label,'message')]")
        );

        if (messageBox == null) {
            throw new RuntimeException("Cannot find chat message input");
        }

        typeIntoEditor(messageBox, message);

        boolean sentByButton = clickFirstVisible(5,
                By.cssSelector("button.send-btn"),
                By.xpath("//*[self::button or @role='button'][contains(@aria-label,'Send') or contains(@title,'Send') "
                        + "or contains(@aria-label,'G\u1eedi') or contains(@title,'G\u1eedi') "
                        + "or contains(normalize-space(.),'G\u1eedi') or contains(normalize-space(.),'Send')]"),
                By.cssSelector("button[type='submit']")
        );

        if (!sentByButton) {
            messageBox.sendKeys(Keys.ENTER);
        }

        assertAnyText(message);
    }

    private String resolveSecret(String value, String propertyKey, String envKey) {
        if (!isPlaceholder(value) && !isBlank(value)) {
            return value.trim();
        }

        String fromProperty = ConfigReader.getProperty(propertyKey);
        if (!isBlank(fromProperty)) {
            return fromProperty.trim();
        }

        String fromSystemProperty = System.getProperty(propertyKey);
        if (!isBlank(fromSystemProperty)) {
            return fromSystemProperty.trim();
        }

        String fromEnv = System.getenv(envKey);
        if (!isBlank(fromEnv)) {
            return fromEnv.trim();
        }

        if (ConfigReader.getBoolean("interactive.otp", false)) {
            Console console = System.console();
            if (console != null) {
                return console.readLine("Enter %s: ", propertyKey).trim();
            }
        }

        return "";
    }

    public void assertElementVisible(By by) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    public void assertUrlContains(String expectedPart) {
        if (!wait.until(driver -> driver.getCurrentUrl().contains(expectedPart))) {
            throw new AssertionError("Current URL does not contain: " + expectedPart);
        }
    }

    public void assertUrlEquals(String expectedUrl) {
        if (!wait.until(driver -> driver.getCurrentUrl().equals(expectedUrl))) {
            throw new AssertionError("Current URL does not equal: " + expectedUrl + ". Actual: " + currentUrl());
        }
    }

    public void assertTitleContains(String expectedPart) {
        if (!wait.until(driver -> driver.getTitle().contains(expectedPart))) {
            throw new AssertionError("Page title does not contain: " + expectedPart + ". Actual: " + driver.getTitle());
        }
    }

    public void assertTitleEquals(String expectedTitle) {
        if (!wait.until(driver -> driver.getTitle().equals(expectedTitle))) {
            throw new AssertionError("Page title does not equal: " + expectedTitle + ". Actual: " + driver.getTitle());
        }
    }

    public void assertAnyText(String expectedTexts) {
        List<String> candidates = Arrays.stream(expectedTexts.split("\\|"))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("assertAnyText requires at least one expected text");
        }

        WebDriverWait assertWait = new WebDriverWait(driver, Duration.ofSeconds(ConfigReader.getInt("assert.wait", 30)));
        try {
            assertWait.until(driver -> {
                String actualText = normalizeForAssertion(getVisibleText() + " " + driver.getPageSource());
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

    public void assertMessageVisible() {
        WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
        boolean visible = shortWait.until(driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                "const selectors = ["
                        + "'[role=\"alert\"]',"
                        + "'[role=\"status\"]',"
                        + "'[role=\"dialog\"]',"
                        + "'.toast',"
                        + "'.Toastify__toast',"
                        + "'[class*=\"toast\" i]',"
                        + "'[class*=\"notification\" i]',"
                        + "'[class*=\"message\" i]',"
                        + "'[class*=\"alert\" i]',"
                        + "'[class*=\"tooltip\" i]',"
                        + "'[class*=\"tippy\" i]'"
                        + "];"
                        + "const isVisible = (el) => {"
                        + "  const style = window.getComputedStyle(el);"
                        + "  const rect = el.getBoundingClientRect();"
                        + "  return style && style.display !== 'none' && style.visibility !== 'hidden' "
                        + "    && Number(style.opacity || 1) > 0 && rect.width > 0 && rect.height > 0;"
                        + "};"
                        + "for (const selector of selectors) {"
                        + "  for (const el of document.querySelectorAll(selector)) {"
                        + "    if (isVisible(el) && (el.innerText || el.textContent || '').trim().length > 0) return true;"
                        + "  }"
                        + "}"
                        + "return false;"
        ));

        if (!visible) {
            throw new AssertionError("No visible message/toast/notification was displayed");
        }
    }

    public void assertTextContains(By by, String expectedText) {
        assertText(by, expectedText, "CONTAINS");
    }

    public void assertText(By by, String expectedText, String assertion) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
        String actual = element.getText();
        String normalizedActual = normalizeForAssertion(actual);
        List<String> candidates = Arrays.stream(expectedText.split("\\|"))
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

    private boolean clickFirstVisible(int timeoutSeconds, By... locators) {
        for (By locator : locators) {
            if (clickIfVisible(locator, timeoutSeconds)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVisible(By by, int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            shortWait.until(ExpectedConditions.visibilityOfElementLocated(by));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private WebElement findFirstVisible(int timeoutSeconds, By... locators) {
        for (By locator : locators) {
            try {
                WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
                return shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            } catch (Exception ignored) {
                // Try the next candidate locator.
            }
        }
        return null;
    }

    private WebElement findFirstVisibleAcross(int timeoutSeconds, By... locators) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(currentDriver -> {
                for (By locator : locators) {
                    try {
                        for (WebElement element : currentDriver.findElements(locator)) {
                            if (element.isDisplayed()) {
                                return element;
                            }
                        }
                    } catch (Exception ignored) {
                        // Try the next candidate locator in the same poll cycle.
                    }
                }
                return null;
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private WebElement findUploadInput(By triggerOrInput, int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(currentDriver -> {
                try {
                    for (WebElement element : currentDriver.findElements(triggerOrInput)) {
                        if ("input".equalsIgnoreCase(element.getTagName())
                                && "file".equalsIgnoreCase(element.getAttribute("type"))) {
                            return element;
                        }
                        for (WebElement nestedInput : element.findElements(By.cssSelector("input[type='file']"))) {
                            return nestedInput;
                        }
                    }
                } catch (Exception ignored) {
                    // Fall back to global file inputs below.
                }

                try {
                    List<WebElement> inputs = currentDriver.findElements(By.cssSelector("input[type='file']"));
                    if (!inputs.isEmpty()) {
                        return inputs.get(inputs.size() - 1);
                    }
                } catch (Exception ignored) {
                    // Keep polling until timeout.
                }

                return null;
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private void waitForAnyElement(By locator, int timeoutSeconds) {
        WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
        shortWait.until(driver -> !driver.findElements(locator).isEmpty());
    }

    private void waitForPageStable(int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            shortWait.until(driver -> ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete"));
        } catch (Exception ignored) {
            // Dynamic SPA screens can keep loading resources; this is only a short best-effort wait.
        }
    }

    private boolean clickCreateGroupChatByScript() {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(15));
            return shortWait.until(driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "const visible = (el) => {"
                            + " if (!el) return false;"
                            + " const style = window.getComputedStyle(el);"
                            + " const rect = el.getBoundingClientRect();"
                            + " return style.display !== 'none' && style.visibility !== 'hidden' "
                            + "   && Number(style.opacity || 1) > 0 && rect.width > 0 && rect.height > 0;"
                            + "};"
                            + "let target = document.querySelector('div.create-room.cursor-pointer, .create-room');"
                            + "if (!visible(target)) {"
                            + "  const headerButtons = [...document.querySelectorAll('.header .flex.items-center.gap-2 .cursor-pointer')].filter(visible);"
                            + "  target = headerButtons.find(el => el.classList.contains('create-room')) || headerButtons[1] || null;"
                            + "}"
                            + "if (!visible(target)) {"
                            + "  const svgs = [...document.querySelectorAll('.header svg[viewBox=\"0 0 34 34\"]')].filter(visible);"
                            + "  const svg = svgs.find(s => (s.parentElement && s.parentElement.classList.contains('create-room'))) || svgs[1] || null;"
                            + "  target = svg ? (svg.closest('.cursor-pointer,button,[role=\"button\"]') || svg) : null;"
                            + "}"
                            + "if (!visible(target)) return false;"
                            + "target.scrollIntoView({block:'center', inline:'center'});"
                            + "const rect = target.getBoundingClientRect();"
                            + "const opts = {bubbles:true,cancelable:true,clientX:rect.left+rect.width/2,clientY:rect.top+rect.height/2};"
                            + "for (const type of ['pointerover','mouseover','pointerdown','mousedown','pointerup','mouseup','click']) {"
                            + "  target.dispatchEvent(new MouseEvent(type, opts));"
                            + "}"
                            + "return true;"
            ));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean waitForCreateGroupChatOpened(int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(driver -> isCreateGroupChatOpened());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void clickFirstElement(String errorMessage, int timeoutSeconds, By... locators) {
        RuntimeException lastError = null;
        for (By locator : locators) {
            try {
                WebElement element = findFirstVisible(timeoutSeconds, locator);
                if (element == null) {
                    continue;
                }
                scrollIntoView(element);
                if (clickElement(element)) {
                    return;
                }
            } catch (Exception e) {
                lastError = new RuntimeException(errorMessage + " with locator: " + locator, e);
            }
        }
        throw new RuntimeException(errorMessage, lastError);
    }

    private void clickAccountSearchResult(String account, WebElement searchInput) {
        List<By> resultLocators = List.of(
                By.xpath("//p[contains(@class,'department') and normalize-space(.)=" + xpathLiteral(account)
                        + "]/ancestor::div[contains(@class,'element-user-info')][1]"),
                By.xpath("//p[contains(@class,'department') and contains(normalize-space(.), " + xpathLiteral(account)
                        + ")]/ancestor::div[contains(@class,'element-user-info')][1]"),
                By.xpath("//div[contains(@class,'element-user-info')][contains(normalize-space(.), " + xpathLiteral(account) + ")]"),
                By.xpath("//*[normalize-space(.)=" + xpathLiteral(account)
                        + "]/ancestor::*[contains(@class,'element-user-info') or contains(@class,'cursor-pointer')][1]"),
                By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(account)
                        + ")]/ancestor::*[contains(@class,'element-user-info') or contains(@class,'cursor-pointer')][1]")
        );

        RuntimeException lastError = null;
        WebElement result = findFirstVisibleAcross(8, resultLocators.toArray(new By[0]));
        if (result != null) {
            scrollIntoView(result);
            if (clickElement(result)) {
                return;
            }
            lastError = new RuntimeException("Account result is visible but cannot be clicked");
        }

        try {
            searchInput.sendKeys(Keys.ARROW_DOWN);
            searchInput.sendKeys(Keys.ENTER);
            return;
        } catch (Exception e) {
            lastError = new RuntimeException("Cannot select account result by keyboard", e);
        }

        throw new RuntimeException("Cannot click account search result for: " + account, lastError);
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
        try {
            WebElement clickable = (WebElement) ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].closest('button,a,[role=\"button\"],label,[role=\"switch\"],.cursor-pointer') || arguments[0];",
                    element);
            scrollIntoView(clickable);
            if (clickElement(clickable)) {
                return true;
            }
        } catch (Exception ignored) {
            // Continue with coordinate-based fallback.
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

    private boolean isCreateGroupChatOpened() {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(2));
            return shortWait.until(driver ->
                    !driver.findElements(By.cssSelector("span.slider.round")).isEmpty()
                            || pageContainsAny("T\u1ea1o nh\u00f3m", "T\u00ean nh\u00f3m", "B\u1ea3o m\u1eadt", "Create group", "Group name", "Security")
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isAppReady() {
        try {
            if (isSecurityKeySetupPage()) {
                return false;
            }
            return !driver.findElements(By.cssSelector(".create-room, p[data-placeholder='Search'], button.send-btn, span.slider.round")).isEmpty()
                    || pageContainsAny("Chats", "Search", "Message", "H\u1ed9i tho\u1ea1i", "Tin nh\u1eafn");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isSecurityKeySetupPage() {
        try {
            String url = currentUrl();
            return url.contains("/auth/key/passphrase")
                    || url.contains("/auth/key/setup")
                    || pageContainsAny("Kh\u00f3a m\u00e3 h\u00f3a", "T\u1ea1o m\u00e3 v\u00e0 sao l\u01b0u", "B\u1ecf qua");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isAuthenticatedOrAppReady() {
        return isSecurityKeySetupPage() || isAppReady();
    }

    private void ensureAppReadyBeforeAction(String actionName) {
        String currentWindow = "";
        try {
            currentWindow = driver.getWindowHandle();
        } catch (Exception ignored) {
            // Continue with the active window.
        }

        handleStaySignedInPrompt(currentWindow);

        if (waitForAppReadyAcross(ConfigReader.getInt("app.ready.wait", 30))) {
            return;
        }

        throw new RuntimeException("App is not ready before action: " + actionName
                + ". Current URL: " + currentUrl()
                + ". Page text sample: " + textSample(getVisibleText()));
    }

    private boolean waitForAppReady(int timeoutSeconds) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return shortWait.until(driver -> isAppReady());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean waitForAppReadyAcross(int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        String originalWindow = "";
        try {
            originalWindow = driver.getWindowHandle();
        } catch (Exception ignored) {
            // Continue with the active window.
        }

        while (System.currentTimeMillis() < deadline) {
            try {
                handleStaySignedInPrompt(originalWindow);
                for (String window : driver.getWindowHandles()) {
                    try {
                        driver.switchTo().window(window);
                        if (isAppReady()) {
                            return true;
                        }
                    } catch (Exception ignored) {
                        // Windows can close while Microsoft redirects back to the app.
                    }
                }
            } catch (Exception ignored) {
                // Retry until the timeout expires.
            }
            waitForMilliseconds(500);
        }

        switchBackIfPossible(originalWindow);
        return false;
    }

    private void scrollIntoView(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
        } catch (Exception ignored) {
            // Scrolling is a best-effort helper before clicking dynamic result rows.
        }
    }

    private void hover(WebElement element) {
        try {
            new org.openqa.selenium.interactions.Actions(driver).moveToElement(element).pause(Duration.ofMillis(300)).perform();
        } catch (Exception ignored) {
            // Tooltip hover is only a helper for icon-only actions.
        }
    }

    private void typeIntoEditor(WebElement element, String value) {
        element.click();
        WebElement activeElement = driver.switchTo().activeElement();
        activeElement.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        activeElement.sendKeys(value);
    }

    private boolean switchToWindowWithVisibleElement(By by, int timeoutSeconds) {
        if (isVisible(by, timeoutSeconds)) {
            return true;
        }

        String originalWindow = driver.getWindowHandle();
        for (String window : driver.getWindowHandles()) {
            driver.switchTo().window(window);
            if (isVisible(by, 1)) {
                return true;
            }
        }
        driver.switchTo().window(originalWindow);
        return false;
    }

    private boolean pageContainsAny(String... texts) {
        try {
            String pageSource = normalizeForAssertion(driver.getPageSource());
            for (String text : texts) {
                if (pageSource.contains(normalizeForAssertion(text))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
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
        String compact = (text == null ? "" : text).replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }

    private boolean isPlaceholder(String value) {
        return value != null && value.startsWith("${") && value.endsWith("}");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }

        String[] parts = value.split("'");
        StringBuilder concat = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                concat.append(", \"'\", ");
            }
            concat.append("'").append(parts[i]).append("'");
        }
        concat.append(")");
        return concat.toString();
    }

    private String normalizeForAssertion(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }
}

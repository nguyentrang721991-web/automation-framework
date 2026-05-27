package core.engine;

import core.keywords.Actions;
import core.utils.ScreenshotUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KeywordEngine {

    private final WebDriver driver;
    private final Actions action;
    private final Map<String, String> objectRepository;

    public KeywordEngine(WebDriver driver) {
        this(driver, Collections.emptyMap());
    }

    public KeywordEngine(WebDriver driver, Map<String, String> objectRepository) {
        this.driver = driver;
        this.action = new Actions(driver);
        this.objectRepository = objectRepository == null ? Collections.emptyMap() : objectRepository;
    }

    public void executeSteps(List<Map<String, String>> steps, Map<String, String> testData) {
        DataEngine dataEngine = new DataEngine(testData);

        for (int i = 0; i < steps.size(); i++) {
            Map<String, String> step = steps.get(i);

            String keyword = firstNonBlank(step.get("action"), step.get("keyword"));
            String object = resolveObject(firstNonBlank(step.get("target"), step.get("object")), dataEngine);
            String input = dataEngine.resolve(firstNonBlank(step.get("input"), step.get("data")));
            String expected = dataEngine.resolve(valueOf(step.get("expected")));
            String assertion = valueOf(step.get("assertion"));

            if (keyword.isEmpty()) {
                continue;
            }

            String normalizedKeyword = normalizeAction(keyword);
            String value = valueForAction(normalizedKeyword, input, expected);
            ensureResolved(normalizedKeyword, object, input, expected);

            String stepNumber = valueOf(step.get("step"));
            String displayStep = stepNumber.isEmpty() ? String.format("%02d", i + 1) : stepNumber;
            System.out.printf("Step %s: %s | %s | %s%n",
                    displayStep, keyword, object, maskIfSensitive(normalizedKeyword, object, value));

            switch (normalizedKeyword) {
                case "navigate":
                case "open":
                case "goto":
                case "openurl":
                    action.navigate(value);
                    break;

                case "openbrowser":
                    // Driver lifecycle is controlled by TestRunner/DriverFactory. Keep this keyword for Template_TC compatibility.
                    break;

                case "click":
                    action.click(toBy(object));
                    break;

                case "clickjs":
                case "clickdeep":
                    action.clickDeep(toBy(object), parseInt(value, 10));
                    break;

                case "clickifvisible":
                    action.clickIfVisible(toBy(object), parseInt(value, 5));
                    break;

                case "input":
                case "type":
                    if (object.isEmpty() && (value.startsWith("http://") || value.startsWith("https://"))) {
                        action.navigate(value);
                        break;
                    }
                    action.input(toBy(object), value);
                    break;

                case "inputeditor":
                case "typeeditor":
                    action.inputEditor(toBy(object), value);
                    break;

                case "uploadfile":
                case "upload":
                case "choosefile":
                    action.uploadFile(toBy(object), value);
                    break;

                case "wait":
                case "sleep":
                    action.waitForSeconds(parseInt(value, 1));
                    break;

                case "loginwithmicrosoft":
                    String[] credentials = value.split("\\|", 2);
                    if (credentials.length != 2) {
                        throw new IllegalArgumentException("loginwithmicrosoft data must be: ${email}|${password}");
                    }
                    action.loginWithMicrosoft(credentials[0].trim(), credentials[1].trim());
                    break;

                case "entermicrosoftotp":
                case "enterotp":
                case "entermicrosoftpin":
                    action.enterMicrosoftOtp(value);
                    break;

                case "enterpin":
                case "enterapppin":
                case "enterpassphase":
                    action.enterAppPin(value);
                    break;

                case "skipsecuritykeysetup":
                case "skipkeysetup":
                case "skipsetupkey":
                    action.skipSecurityKeySetup();
                    break;

                case "searchandopenaccount":
                case "openchatbyaccount":
                case "searchaccount":
                    action.searchAndOpenAccount(value);
                    break;

                case "sendchatmessage":
                case "sendmessage":
                    action.sendChatMessage(value);
                    break;

                case "opencreategroupchat":
                case "creategroupchat":
                case "clickcreategroup":
                    action.openCreateGroupChat();
                    break;

                case "clicksecuritylock":
                case "togglesecuritylock":
                case "enableencryptedchat":
                    action.clickSecurityLockToggle();
                    break;

                case "assertvisible":
                case "assertelementvisible":
                case "verifyvisible":
                case "verifyelementvisible":
                    action.assertElementVisible(toBy(object));
                    break;

                case "asserttextcontains":
                    action.assertText(toBy(object), value, "CONTAINS");
                    break;

                case "asserttextequals":
                    action.assertText(toBy(object), value, "EQUALS");
                    break;

                case "verifytext":
                    if (object.isEmpty()) {
                        action.assertAnyText(value);
                    } else {
                        action.assertText(toBy(object), value, assertion);
                    }
                    break;

                case "assertanytext":
                case "verifypagetext":
                    action.assertAnyText(value);
                    break;

                case "assertmessagevisible":
                case "asserttoastvisible":
                case "assertnotificationvisible":
                    action.assertMessageVisible();
                    break;

                case "asserturlcontains":
                    action.assertUrlContains(value);
                    break;

                case "asserturlequals":
                    action.assertUrlEquals(value);
                    break;

                case "verifyurl":
                    if (isEqualsAssertion(assertion)) {
                        action.assertUrlEquals(value);
                    } else {
                        action.assertUrlContains(value);
                    }
                    break;

                case "verifytitle":
                    if (isEqualsAssertion(assertion)) {
                        action.assertTitleEquals(value);
                    } else {
                        action.assertTitleContains(value);
                    }
                    break;

                case "asserttitlecontains":
                    action.assertTitleContains(value);
                    break;

                case "asserttitleequals":
                    action.assertTitleEquals(value);
                    break;

                case "screenshot":
                case "capturescreenshot":
                    ScreenshotUtils.capture(driver, value.isEmpty() ? "step_" + displayStep : value);
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported keyword: " + keyword);
            }
        }
    }

    private String resolveObject(String rawObject, DataEngine dataEngine) {
        String object = dataEngine.resolve(valueOf(rawObject));
        if (object.isEmpty()) {
            return "";
        }

        String referencedLocator = objectRepository.get(object);
        if (referencedLocator != null) {
            return dataEngine.resolve(referencedLocator);
        }

        if (isRawLocator(object)) {
            return object;
        }

        throw new IllegalArgumentException("Target is not defined in ObjectRepository: " + object);
    }

    private By toBy(String object) {
        if (object == null || object.trim().isEmpty()) {
            throw new IllegalArgumentException("Locator object is required for this keyword");
        }

        String locator = object.trim();
        String lower = locator.toLowerCase(Locale.ROOT);

        if (lower.startsWith("xpath=")) {
            return By.xpath(locator.substring(6));
        }
        if (lower.startsWith("css=")) {
            return By.cssSelector(locator.substring(4));
        }
        if (lower.startsWith("id=")) {
            return By.id(locator.substring(3));
        }
        if (lower.startsWith("name=")) {
            return By.name(locator.substring(5));
        }
        if (lower.startsWith("class=")) {
            return By.className(locator.substring(6));
        }
        if (lower.startsWith("tag=")) {
            return By.tagName(locator.substring(4));
        }
        if (lower.startsWith("text=")) {
            return By.xpath(textLocator(locator.substring(5)));
        }
        if (locator.startsWith("/") || locator.startsWith("(")) {
            return By.xpath(locator);
        }
        return By.xpath(locator);
    }

    private String textLocator(String pipeSeparatedText) {
        StringBuilder condition = new StringBuilder();
        for (String part : pipeSeparatedText.split("\\|")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }

            if (condition.length() > 0) {
                condition.append(" or ");
            }
            condition.append("contains(normalize-space(.), ").append(xpathLiteral(text)).append(")");
        }

        if (condition.length() == 0) {
            throw new IllegalArgumentException("text= locator requires at least one text value");
        }

        return "//*[self::button or self::a or @role='button'][" + condition + "]";
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

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String valueOf(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        String firstValue = valueOf(first);
        if (!firstValue.isEmpty()) {
            return firstValue;
        }
        return valueOf(second);
    }

    private String valueForAction(String normalizedKeyword, String input, String expected) {
        if ((normalizedKeyword.startsWith("assert") || normalizedKeyword.startsWith("verify"))
                && !valueOf(expected).isEmpty()) {
            return expected;
        }
        return valueOf(input);
    }

    private String normalizeAction(String keyword) {
        return valueOf(keyword).toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    private boolean isRawLocator(String locator) {
        String value = valueOf(locator);
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("xpath=")
                || lower.startsWith("css=")
                || lower.startsWith("id=")
                || lower.startsWith("name=")
                || lower.startsWith("class=")
                || lower.startsWith("tag=")
                || lower.startsWith("text=")
                || value.startsWith("/")
                || value.startsWith("(");
    }

    private boolean isEqualsAssertion(String assertion) {
        String normalized = valueOf(assertion).toLowerCase(Locale.ROOT);
        return normalized.equals("equals") || normalized.equals("equal") || normalized.equals("eq") || normalized.equals("=");
    }

    private void ensureResolved(String normalizedKeyword, String object, String input, String expected) {
        if ("entermicrosoftotp".equals(normalizedKeyword)) {
            return;
        }

        failIfUnresolved("Target", object);
        failIfUnresolved("Input", input);
        failIfUnresolved("Expected", expected);
    }

    private void failIfUnresolved(String fieldName, String value) {
        if (valueOf(value).contains("${")) {
            throw new IllegalArgumentException(fieldName + " contains unresolved TestData placeholder: " + value);
        }
    }

    private String maskIfSensitive(String keyword, String object, String value) {
        String target = valueOf(object).toLowerCase(Locale.ROOT);
        if (keyword.contains("login")
                || keyword.contains("pin")
                || keyword.contains("otp")
                || keyword.contains("passphase")
                || keyword.contains("passphrase")
                || keyword.contains("password")
                || target.contains("pin")
                || target.contains("password")) {
            return "***";
        }
        return value;
    }
}

package core.engine;

import core.keywords.Actions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KeywordEngine {

    private final Actions action;
    private final Map<String, String> objectRepository;

    public KeywordEngine(WebDriver driver) {
        this(driver, Collections.emptyMap());
    }

    public KeywordEngine(WebDriver driver, Map<String, String> objectRepository) {
        this.action = new Actions(driver);
        this.objectRepository = objectRepository == null ? Collections.emptyMap() : objectRepository;
    }

    public void executeSteps(List<Map<String, String>> steps, Map<String, String> testData) {
        DataEngine dataEngine = new DataEngine(testData);

        for (int i = 0; i < steps.size(); i++) {
            Map<String, String> step = steps.get(i);

            String keyword = valueOf(step.get("keyword"));
            String object = resolveObject(valueOf(step.get("object")), dataEngine);
            String value = dataEngine.resolve(valueOf(step.get("data")));

            if (keyword.isEmpty()) {
                continue;
            }

            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT).trim();
            System.out.printf("Step %02d: %s | %s | %s%n", i + 1, keyword, object, maskIfSensitive(normalizedKeyword, value));

            switch (normalizedKeyword) {
                case "navigate":
                case "open":
                case "goto":
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
                    action.assertElementVisible(toBy(object));
                    break;

                case "asserttextcontains":
                    action.assertTextContains(toBy(object), value);
                    break;

                case "assertanytext":
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

                default:
                    throw new IllegalArgumentException("Unsupported keyword: " + keyword);
            }
        }
    }

    private String resolveObject(String rawObject, DataEngine dataEngine) {
        String object = valueOf(rawObject);
        if (object.isEmpty()) {
            return "";
        }

        String referencedLocator = objectRepository.getOrDefault(object, object);
        return dataEngine.resolve(referencedLocator);
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

    private String maskIfSensitive(String keyword, String value) {
        if (keyword.contains("login") || keyword.contains("pin") || keyword.contains("otp") || keyword.contains("passphase")) {
            return "***";
        }
        return value;
    }
}

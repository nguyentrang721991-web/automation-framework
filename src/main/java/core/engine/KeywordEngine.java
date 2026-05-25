package core.engine;

import core.keywords.WebActions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class KeywordEngine {

    private WebDriver driver;
    private WebActions actions;

    public KeywordEngine(WebDriver driver) {
        this.driver = driver;
        this.actions = new WebActions(driver);
    }

    /**
     * Execute list of test steps from Excel
     */
    public void executeSteps(List<Map<String, String>> steps) {
        for (Map<String, String> step : steps) {

            String action = step.get("Action");
            String locatorType = step.get("LocatorType");
            String locatorValue = step.get("LocatorValue");
            String value = step.get("Value");

            By by = buildBy(locatorType, locatorValue);

            try {
                System.out.println("👉 Executing: " + action);

                invokeAction(action, by, value);

            } catch (Exception e) {
                throw new RuntimeException("❌ Failed at step: " + action + " | Error: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Build Selenium By object from locator type
     */
    private By buildBy(String type, String value) {
        if (type == null || value == null) return null;

        switch (type.toLowerCase()) {
            case "id":
                return By.id(value);
            case "name":
                return By.name(value);
            case "xpath":
                return By.xpath(value);
            case "css":
                return By.cssSelector(value);
            case "classname":
                return By.className(value);
            case "tagname":
                return By.tagName(value);
            case "linktext":
                return By.linkText(value);
            case "partiallinktext":
                return By.partialLinkText(value);
            default:
                throw new RuntimeException("❌ Unsupported locator type: " + type);
        }
    }

    /**
     * Invoke action dynamically using reflection
     */
    private void invokeAction(String actionName, By by, String value) throws Exception {

        Method method;

        // Case 1: method(By, String)
        if (by != null && value != null && !value.isEmpty()) {
            try {
                method = WebActions.class.getMethod(actionName, By.class, String.class);
                method.invoke(actions, by, value);
                return;
            } catch (NoSuchMethodException ignored) {}
        }

        // Case 2: method(By)
        if (by != null) {
            try {
                method = WebActions.class.getMethod(actionName, By.class);
                method.invoke(actions, by);
                return;
            } catch (NoSuchMethodException ignored) {}
        }

        // Case 3: method(String)
        if (value != null && !value.isEmpty()) {
            try {
                method = WebActions.class.getMethod(actionName, String.class);
                method.invoke(actions, value);
                return;
            } catch (NoSuchMethodException ignored) {}
        }

        // Case 4: method()
        try {
            method = WebActions.class.getMethod(actionName);
            method.invoke(actions);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("❌ No matching method found for action: " + actionName);
        }
    }
}
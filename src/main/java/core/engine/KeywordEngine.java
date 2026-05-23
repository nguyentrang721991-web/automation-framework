package core.engine;

import core.keywords.Actions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.Map;

public class KeywordEngine {

    private WebDriver driver;
    private Actions action;

    public KeywordEngine(WebDriver driver) {
        this.driver = driver;
        this.action = new Actions(driver);
    }

    public void executeSteps(List<Map<String, String>> steps, Map<String, String> testData) {

        DataEngine dataEngine = new DataEngine(testData);

        for (Map<String, String> step : steps) {

            String keyword = step.get("action");   // FIX
            String object = step.get("locator");   // FIX
            String value = step.get("value");

            value = dataEngine.resolve(value);

            System.out.println("STEP: " + keyword + " | " + object + " | " + value);

            By by = null;
            if (object != null && !object.isEmpty()) {
                by = By.xpath(object);
            }

            switch (keyword.toLowerCase()) {

                case "openbrowser":
                    driver.get(value);
                    break;

                case "click":
                    if (by == null) throw new RuntimeException("Locator null for click");
                    action.click(by);
                    break;

                case "input":
                    if (by == null) throw new RuntimeException("Locator null for input");
                    action.input(by, value);
                    break;

                case "wait":
                    try {
                        Thread.sleep(Long.parseLong(value));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;

                default:
                    System.out.println("❌ Unknown keyword: " + keyword);
            }
        }
    }
}
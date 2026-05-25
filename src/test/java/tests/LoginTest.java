package tests;

import base.BaseTest;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test(enabled = false, description = "Placeholder test disabled; Excel-driven login cases run from runner.TestRunner")
    public void testLogin() {
        driver.get("https://lms.scotsenglish.vn/");
    }
}

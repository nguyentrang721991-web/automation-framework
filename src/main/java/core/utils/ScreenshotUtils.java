package core.utils;

import org.openqa.selenium.*;
import java.io.File;
import org.apache.commons.io.FileUtils;

public class ScreenshotUtils {

    public static void capture(WebDriver driver, String name){
        try{
            File directory = new File("screenshots");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new RuntimeException("Cannot create screenshots directory: " + directory.getAbsolutePath());
            }
            String safeName = name == null || name.trim().isEmpty()
                    ? "screenshot"
                    : name.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
            File src = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(src, new File(directory, safeName + ".png"));
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}

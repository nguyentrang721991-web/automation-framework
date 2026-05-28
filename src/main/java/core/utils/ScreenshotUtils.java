package core.utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScreenshotUtils {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private ScreenshotUtils() {
    }

    public static String capture(WebDriver driver, String name) {
        try {
            Path directory = Paths.get(ConfigReader.getProperty("report.screenshot.dir", "reports/screenshots"));
            Files.createDirectories(directory);

            String safeName = name == null || name.trim().isEmpty()
                    ? "screenshot"
                    : name.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");

            String fileName = safeName + "_" + LocalDateTime.now().format(TIMESTAMP) + ".png";
            Path destination = directory.resolve(fileName);
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(src.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toAbsolutePath().toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

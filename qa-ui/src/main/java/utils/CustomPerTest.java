package utils;

import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.testng.BrowserPerTest;
import io.qameta.allure.Allure;
import io.qameta.allure.selenide.AllureSelenide;
import io.qameta.allure.selenide.LogType;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.Optional;

import static com.codeborne.selenide.WebDriverRunner.closeWebDriver;
import static io.qameta.allure.Allure.addAttachment;
import static java.lang.Thread.sleep;
import static utils.CustomAllureSelenide.generateHtmlVideoReport;
import static utils.CustomAllureSelenide.getVideoUrl;

public class CustomPerTest extends BrowserPerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureSelenide.class);

    @Override
    public void onTestFailure(ITestResult result) {
        super.onTestFailure(result);
        getScreenshotBytes().ifPresent(bytes -> Allure.getLifecycle().addAttachment("Screenshot", "image/png", "png", bytes));
        addAttachment("Video report", "text/html", new ByteArrayInputStream(generateHtmlVideoReport(getVideoUrl()).getBytes()), ".html");
        addAttachment("Console log", "text/plain", analyzeLog());
        closeWebDriver();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        super.onTestSuccess(result);
        addAttachment("Video report", "text/html", new ByteArrayInputStream(generateHtmlVideoReport(getVideoUrl()).getBytes()), ".html");
        addAttachment("Console log", "text/plain", analyzeLog());
        try {
            sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        closeWebDriver();
    }

    private static Optional<byte[]> getScreenshotBytes() {
        try {
            return WebDriverRunner.hasWebDriverStarted()
                    ? Optional.of(((TakesScreenshot) WebDriverRunner.getWebDriver()).getScreenshotAs(OutputType.BYTES))
                    : Optional.empty();
        } catch (WebDriverException e) {
            LOGGER.warn("Could not get screen shot", e);
            return Optional.empty();
        }
    }

    public String analyzeLog() {
        StringBuilder builder = new StringBuilder();
        LogEntries logEntries = WebDriverRunner.getWebDriver().manage().logs().get(String.valueOf(LogType.BROWSER));
        for (LogEntry entry : logEntries) {
            builder.append(new Date(entry.getTimestamp()))
                    .append(entry.getLevel())
                    .append(entry.getMessage())
                    .append("\n");
        }
        return builder.toString();
    }
}

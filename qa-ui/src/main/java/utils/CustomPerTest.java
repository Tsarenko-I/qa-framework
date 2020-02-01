package utils;

import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.testng.BrowserPerTest;
import io.qameta.allure.Allure;
import io.qameta.allure.selenide.AllureSelenide;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static com.codeborne.selenide.WebDriverRunner.closeWebDriver;
import static io.qameta.allure.Allure.addAttachment;
import static utils.CustomAllureSelenide.generateHtmlVideoReport;
import static utils.CustomAllureSelenide.getVideoUrl;

public class CustomPerTest extends BrowserPerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureSelenide.class);

    @Override
    public void onTestFailure(ITestResult result) {
        super.onTestFailure(result);
        getScreenshotBytes().ifPresent(bytes -> Allure.getLifecycle().addAttachment("Screenshot", "image/png", "png", bytes));
        addAttachment("Video report", "text/html", new ByteArrayInputStream(generateHtmlVideoReport(getVideoUrl()).getBytes()), ".html");
        closeWebDriver();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        super.onTestSuccess(result);
        addAttachment("Video report", "text/html", new ByteArrayInputStream(generateHtmlVideoReport(getVideoUrl()).getBytes()), ".html");
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
}

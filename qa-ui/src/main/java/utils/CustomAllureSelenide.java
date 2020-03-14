package utils;

import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.logevents.LogEvent;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.selenide.AllureSelenide;
import io.qameta.allure.selenide.LogType;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Optional;

import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static io.qameta.allure.Allure.addAttachment;
import static io.qameta.allure.util.ResultsUtils.getStatus;
import static io.qameta.allure.util.ResultsUtils.getStatusDetails;

public class CustomAllureSelenide extends AllureSelenide {
    private final AllureLifecycle lifecycle = Allure.getLifecycle();
    private static final Logger LOGGER = LoggerFactory.getLogger(AllureSelenide.class);

    @Override
    public void afterEvent(final LogEvent event) {
        lifecycle.getCurrentTestCaseOrStep().ifPresent(parentUuid -> {
            switch (event.getStatus()) {
                case PASS:
                    getScreenshotBytes().ifPresent(bytes -> lifecycle.addAttachment("Screenshot", "image/png", "png", bytes));
                    lifecycle.updateStep(step -> step.setStatus(Status.PASSED));
                    addAttachment("Video report", "text/html", new ByteArrayInputStream(generateHtmlVideoReport(getVideoUrl()).getBytes()), ".html");
                    addAttachment("WSConsole log", "text/plain", wsAnalyzeLog());
                    addAttachment("Console log", "text/plain", analyzeLog());
                    break;
                case FAIL:
                    getScreenshotBytes().ifPresent(bytes -> lifecycle.addAttachment("Screenshot", "image/png", "png", bytes));
                    addAttachment("Video report", "text/html", new ByteArrayInputStream(generateHtmlVideoReport(getVideoUrl()).getBytes()), ".html");
                    addAttachment("WSConsole log", "text/plain", wsAnalyzeLog());
                    addAttachment("Console log", "text/plain", analyzeLog());
                    lifecycle.updateStep(stepResult -> {
                        stepResult.setStatus(getStatus(event.getError()).orElse(Status.BROKEN));
                        stepResult.setStatusDetails(getStatusDetails(event.getError()).orElse(new StatusDetails()));
                    });
                    break;
                default:
                    LOGGER.warn("Step finished with unsupported status {}", event.getStatus());
                    break;
            }
            lifecycle.stopStep();
        });
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

    public static URL getVideoUrl() {
        SessionId sessionId = null;
        try {
            sessionId = ((RemoteWebDriver) WebDriverRunner.getWebDriver()).getSessionId();
        } catch (ClassCastException ex) {
            LOGGER.warn("The driver cannot be casted to a RemoteWebDriver. Probably it has been run one type of a WebDriver, for example ChromeDriver, FirefoxDriver and so on");
            LOGGER.warn("Video can be recorded only by the RemoveWedDriver instance which has been to run on the Selenoid infrastructure");
        } catch (IllegalStateException ex) {
            LOGGER.warn(" No webdriver is bound to current thread: 1. You need to call open(url) first.");
            LOGGER.warn("Video can be recorded only by the RemoveWedDriver instance which has been to run on the Selenoid infrastructure");
        }

        URL videoUrl = null;
        try {
            videoUrl = new URL("http://localhost:4444/video/" + sessionId + ".mp4");
        } catch (MalformedURLException e) {
            LOGGER.error("Can't get the video url for session: " + sessionId + " " + e.getMessage());
        }
        return videoUrl;
    }

    public static String generateHtmlVideoReport(URL videoUrl) {
        Document doc = Jsoup.parseBodyFragment("<video><source></video>");
        doc.getElementsByTag("video").get(0)
                .attr("width", "1366")
                .attr("height", "768")
                .attr("controls", "controls");

        doc.getElementsByTag("source").get(0)
                .attr("src", videoUrl.toString())
                .attr("type", "video/mp4");
        return doc.toString();
    }

    public static String analyzeLog() {
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

    public static String wsAnalyzeLog() {
        StringBuilder builder = new StringBuilder();
        LogEntries logEntries = getWebDriver()
                .manage()
                .logs()
                .get(org.openqa.selenium.logging.LogType.PERFORMANCE);
        logEntries.forEach(entry -> {
            JSONObject messageJSON = new JSONObject(entry.getMessage());
            String method = messageJSON.getJSONObject("message").getString("method");
            if (method.equalsIgnoreCase("Network.webSocketFrameSent")) {
                builder
                        .append("Message Sent: ")
                        .append("\n")
                        .append(messageJSON.getJSONObject("message").getJSONObject("params").getJSONObject("response").getString("payloadData"))
                        .append("\n");
            } else if (method.equalsIgnoreCase("Network.webSocketFrameReceived")) {
                builder
                        .append("Message Received: ")
                        .append("\n")
                        .append(messageJSON.getJSONObject("message").getJSONObject("params").getJSONObject("response").getString("payloadData"))
                        .append("\n");
            }
        });
        return builder.toString();
    }
}

package ConfigSelenoid;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverProvider;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.logging.Level;

public class SelenoidDriver{
//public class SelenoidDriver implements WebDriverProvider {
//
//    public WebDriver createDriver(DesiredCapabilities desiredCapabilities) {
//        DesiredCapabilities capabilities = new DesiredCapabilities();
//        capabilities.setBrowserName("chrome");
//        capabilities.setVersion("80.0");
//        capabilities.setCapability("enableVNC", true);
//        capabilities.setCapability("enableVideo", false);
//
//        capabilities = DesiredCapabilities.chrome();
//        LoggingPreferences logPrefs = new LoggingPreferences();
//        logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
//        capabilities.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
//
//        try {
//            RemoteWebDriver driver = new RemoteWebDriver(URI.create("http://selenoid:4444/wd/hub").toURL(), capabilities);
//            return driver;
//        } catch (MalformedURLException var5) {
//            throw new RuntimeException("Can't start selenoid driver " + var5);
//        }
//    }

    public static void initDriver() {
        Configuration.remote = "http://localhost:8080/wd/hub";
        Configuration.driverManagerEnabled = false;
        Configuration.browser = "chrome";
        Configuration.browserVersion = "80";
        Configuration.startMaximized = true;
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability("enableVNC", true);
        capabilities.setCapability("enableVideo", true);
        capabilities.setCapability("sessionTimeout", "5m");

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
        logPrefs.enable(LogType.BROWSER, Level.ALL);
        ChromeOptions options = new ChromeOptions();
        options.setCapability("goog:loggingPrefs", logPrefs);
        capabilities.setCapability(ChromeOptions.CAPABILITY, options);
        options.merge(capabilities);

        Configuration.browserCapabilities = capabilities;
        Configuration.reportsFolder = "target/test-result/reports";
    }
}

package ConfigSelenoid;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverProvider;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URI;

public class SelenoidDriver{
//public class SelenoidDriver implements WebDriverProvider {

//    public WebDriver createDriver(DesiredCapabilities desiredCapabilities) {
//        DesiredCapabilities capabilities = new DesiredCapabilities();
//        capabilities.setBrowserName("chrome");
//        capabilities.setVersion("79.0");
//        capabilities.setCapability("enableVNC", true);
//        capabilities.setCapability("enableVideo", false);
//
//        try {
//            RemoteWebDriver driver = new RemoteWebDriver(URI.create("http://localhost:8080/wd/hub").toURL(), capabilities);
//            return driver;
//        } catch (MalformedURLException var5) {
//            throw new RuntimeException("Can't start selenoid driver " + var5);
//        }
//    }

    public static void initDriver() {
        Configuration.remote = "http://localhost:8080/wd/hub";
        Configuration.driverManagerEnabled = false;
        Configuration.browser = "firefox";
        Configuration.browserVersion = "71";
        Configuration.startMaximized = true;
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability("enableVNC", true);
        capabilities.setCapability("enableVideo", true);
        Configuration.browserCapabilities = capabilities;
        Configuration.reportsFolder = "target/test-result/reports";
    }
}

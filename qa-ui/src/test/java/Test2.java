import ConfigProperty.EnvironmentConfig;
import ConfigSelenoid.SelenoidDriver;
import com.codeborne.selenide.ex.UIAssertionError;
import com.codeborne.selenide.logevents.SelenideLogger;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import pages.MainPage;
import utils.CustomAllureSelenide;

import static com.codeborne.selenide.Selenide.sleep;
import static org.aeonbits.owner.ConfigCache.getOrCreate;

@Feature("Feature 2")
public class Test2 {
    public EnvironmentConfig environmentConfig;

    @BeforeClass
    public void beforeClass() {
        SelenoidDriver.initDriver();
        environmentConfig = getOrCreate(EnvironmentConfig.class);
    }

    @BeforeMethod
    public void beforemethood() {
        SelenideLogger.addListener("AllureSelenide", new CustomAllureSelenide().screenshots(true).savePageSource(false));
    }

    @Test
    @Description("Open Main Page")
    public void main_page_test() throws UIAssertionError {
        MainPage mainPage = new MainPage();
        mainPage.openMainPage(environmentConfig.url());
        sleep(3000);
    }
}

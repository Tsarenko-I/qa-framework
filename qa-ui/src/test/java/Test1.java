import ConfigProperty.EnvironmentConfig;
import ConfigSelenoid.SelenoidDriver;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.logevents.SelenideLogger;
import io.qameta.allure.Description;
import io.qameta.allure.selenide.AllureSelenide;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import pages.MainPage;
import utils.CustomAllureSelenide;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.sleep;
import static org.aeonbits.owner.ConfigCache.getOrCreate;

public class Test1 {
    public EnvironmentConfig environmentConfig;

    @BeforeClass
    public void beforeClass() {
        SelenoidDriver.initDriver();
        environmentConfig = getOrCreate(EnvironmentConfig.class);
    }

    @BeforeMethod
    public void beforemethood(){
        SelenideLogger.addListener("AllureSelenide", new CustomAllureSelenide().screenshots(true).savePageSource(false));
    }

    @Test
    @Description("Open Main Page")
    public void main_page_test() {
        MainPage mainPage = new MainPage();
        mainPage.openMainPage(environmentConfig.url());
        sleep(3000);
//        SelenideElement element = $("dfsdf");
//        element.click();
//        Assert.assertTrue(false);
    }
}

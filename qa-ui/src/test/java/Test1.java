import ConfigProperty.EnvironmentConfig;
import ConfigSelenoid.SelenoidDriver;
import com.codeborne.selenide.ex.UIAssertionError;
import com.codeborne.selenide.logevents.SelenideLogger;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import pages.EditorPage;
import pages.MainPage;
import utils.CustomAllureSelenide;

import static com.codeborne.selenide.Selenide.sleep;
import static org.aeonbits.owner.ConfigCache.getOrCreate;

@Feature("Feature 1")
public class Test1 {
    public EnvironmentConfig environmentConfig;

    @BeforeClass
    public void beforeClass() {
        SelenoidDriver.initDriver();
        environmentConfig = getOrCreate(EnvironmentConfig.class);
    }
//
//    @BeforeMethod
//    public void beforeMethod() {
//        SelenideLogger.addListener("AllureSelenide", new CustomAllureSelenide().screenshots(true).savePageSource(false));
//    }

    @Test
    public void upload_file_test() throws UIAssertionError {
        MainPage mainPage = new MainPage();
        mainPage.openMainPage(environmentConfig.url());
        sleep(20000);
        mainPage.uploadFile("/Users/igor/Desktop/qa-fraimework/qa-ui/src/test/resources/SuperFillableDocNew.pdf");
        EditorPage editorPage = new EditorPage();
        sleep(20000);
        CustomAllureSelenide.wsAnalyzeLog();
        sleep(5000);
    }
}

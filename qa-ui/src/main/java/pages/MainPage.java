package pages;


import io.qameta.allure.Step;

import static com.codeborne.selenide.Selenide.open;
import org.testng.asserts.*;

public class MainPage {

    @Step("Open browser and Main Page")
    public void openMainPage(String urlMainPage) {
        open(urlMainPage);
        Assertion assertion = new Assertion();
        assertion.assertTrue(false);
    }
}

package pages;


import io.qameta.allure.Step;

import static com.codeborne.selenide.Selenide.open;

public class MainPage {

    @Step("Open browser and Main Page")
    public void openMainPage(String urlMainPage) {
        open(urlMainPage);
        throw new AssertionError();
    }
}

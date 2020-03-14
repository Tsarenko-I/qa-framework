package pages;


import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import io.qameta.allure.Step;

import java.io.File;

import static com.codeborne.selenide.Selenide.*;

public class MainPage {
    private SelenideElement uploadInput = $(".new-btn.btn--orange #fileupload");

    @Step("Open browser and Main Page")
    public void openMainPage(String urlMainPage) {
        open(urlMainPage);
    }

    @Step("Upload file to editor")
    public void uploadFile(String pathFile) {
        File uploadFile = new File(pathFile);
        switchTo().frame(0);
        uploadInput.uploadFile(uploadFile);
        switchTo().defaultContent();
    }
}

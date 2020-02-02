import com.codeborne.selenide.ex.UIAssertionError;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import okhttp3.OkHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.testng.annotations.Test;

import java.io.IOException;

@Feature("Feature delete selenoid video")
public class DeleteSelenoidVideoTest {

    @Test
    @Description("Open Main Page")
    public void main_page_test() throws UIAssertionError, IOException {
        OkHttpClient client = new OkHttpClient();

        Document doc = Jsoup.connect("http://localhost:4444/video/").get();
        Elements newsHeadlines = doc.select("pre a");
        for (Element headline : newsHeadlines) {
            String text = headline.text();

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("http://localhost:4444/video/" + text).delete()
                    .build();
            client.newCall(request).execute();
        }
    }
}

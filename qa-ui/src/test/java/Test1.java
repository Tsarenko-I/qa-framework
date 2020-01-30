import ConfigProperty.EnvironmentConfig;
import ConfigSelenoid.SelenoidDriver;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.sleep;
import static org.aeonbits.owner.ConfigCache.getOrCreate;

public class Test1 {
    public EnvironmentConfig environmentConfig;

    @BeforeClass
    public void beforeClass() {
        environmentConfig = getOrCreate(EnvironmentConfig.class);
    }

    @Test
    public void test1() {
        SelenoidDriver.initDriver();

        open(environmentConfig.url());

        sleep(20000L);
    }
}

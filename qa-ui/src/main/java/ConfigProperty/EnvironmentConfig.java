package ConfigProperty;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;

@Sources("classpath:ConfigProperty/environment.${application.environment}.properties")
public interface EnvironmentConfig extends Config {
    @Key("url")
    String url();
}

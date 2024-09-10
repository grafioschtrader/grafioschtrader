package grafioschtrader.config;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import grafioschtrader.types.FeatureType;

@Component
@ConfigurationProperties(prefix = "gt.use")
public class FeatureConfig {

  private boolean websocket;
  private boolean algo;
  private boolean alert;

  public Set<FeatureType> getEnabledFeatures() {
    EnumSet<FeatureType> features = EnumSet.noneOf(FeatureType.class);
    if (websocket) {
      features.add(FeatureType.WEBSOCKET);
    }
    if (algo) {
      features.add(FeatureType.ALGO);
    }
    if (alert) {
      features.add(FeatureType.ALERT);
    }
    return features;
  }

  public boolean isWebsocket() {
    return websocket;
  }

  public void setWebsocket(boolean websocket) {
    this.websocket = websocket;
  }

  public boolean isAlgo() {
    return algo;
  }

  public void setAlgo(boolean algo) {
    this.algo = algo;
  }

  public boolean isAlert() {
    return alert;
  }

  public void setAlert(boolean alert) {
    this.alert = alert;
  }
}
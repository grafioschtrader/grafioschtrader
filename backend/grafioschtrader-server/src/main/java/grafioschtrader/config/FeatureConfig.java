package grafioschtrader.config;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import grafiosch.dto.ConfigurationWithLogin.FeatureType;
import grafioschtrader.types.FeatureTypeGT;

@Component
@ConfigurationProperties(prefix = "gt.use")
public class FeatureConfig {

  private boolean websocket;
  private boolean algo;
  private boolean alert;

  public Set<FeatureTypeGT> getEnabledFeatures() {
    EnumSet<FeatureTypeGT> features = EnumSet.noneOf(FeatureTypeGT.class);
    if (websocket) {
      features.add(FeatureTypeGT.WEBSOCKET);
    }
    if (algo) {
      features.add(FeatureTypeGT.ALGO);
    }
    if (alert) {
      features.add(FeatureTypeGT.ALERT);
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
package grafioschtrader.config;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.FeatureTypeGT;

@Component
@ConfigurationProperties(prefix = "gt.use")
public class FeatureConfig {

  @Autowired
  @Lazy
  private GlobalparametersService globalparametersService;

  @Autowired
  @Lazy
  private GlobalparametersJpaRepository globalparametersJpaRepository;

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
    // GTNET is not added here: it is a library feature now and TokenAuthentication raises it from
    // GlobalparametersJpaRepository.isGTNetEnabled() for every application built on the base modules.
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

  /**
   * Checks whether GTNet is enabled by reading from database global parameters.
   *
   * Deliberately uses {@link grafiosch.repository.GlobalparametersJpaRepositoryCustom#isGTNetEnabled()} and not
   * {@code isGTNetOperational()}. This flag is sent to the client at login and drives the whole GTNet section of the
   * admin menu, which contains the setup table where the own GTNet entry is created in the first place. Requiring an
   * own entry here would be a deadlock: no entry means no menu, and no menu means the entry can never be created.
   * Background tasks and the price update paths use {@code isGTNetOperational()} instead, so they stay idle until the
   * setup is complete.
   *
   * @return true if GTNet is enabled in the database, false otherwise
   */
  public boolean isGtnet() {
    return globalparametersJpaRepository.isGTNetEnabled();
  }
}
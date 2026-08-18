package grafioschtrader.connector.instrument.frankfurter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The public Frankfurter instance at <a href="https://api.frankfurter.dev">api.frankfurter.dev</a>. It requires no API
 * key and imposes no monthly quota, only an unspecified protection against abuse.
 *
 * <p>
 * The root URL is injected rather than hard coded, so an installation can point this connector at a mirror or at a
 * self-hosted instance without a code change. It defaults to the public host, hence the connector is always activated.
 * </p>
 */
@Component
public class FrankfurterFeedConnector extends FrankfurterApiFeedConnector {

  /**
   * Creates the connector for the public Frankfurter instance.
   *
   * @param apiRootUrl root URL of the instance without a version path, from {@code gt.datafeed.frankfurter.url}
   */
  public FrankfurterFeedConnector(
      @Value("${gt.datafeed.frankfurter.url:https://api.frankfurter.dev}") String apiRootUrl) {
    super("frankfurter", "Frankfurter", apiRootUrl);
  }
}

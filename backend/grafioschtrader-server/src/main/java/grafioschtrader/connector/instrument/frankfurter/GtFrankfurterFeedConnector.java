package grafioschtrader.connector.instrument.frankfurter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A self-hosted Frankfurter instance operated for Grafioschtrader, running the same API from the
 * {@code lineofflight/frankfurter} image.
 *
 * <p>
 * No URL is configured by default, which leaves the connector deactivated and therefore absent from every connector
 * selection in the user interface. Setting {@code gt.datafeed.gtfrankfurter.url} to the root of the instance is all
 * that is needed to make it selectable, next to and independent of the public
 * {@link FrankfurterFeedConnector}.
 * </p>
 */
@Component
public class GtFrankfurterFeedConnector extends FrankfurterApiFeedConnector {

  /**
   * Creates the connector for the self-hosted instance.
   *
   * @param apiRootUrl root URL of the instance without a version path, from {@code gt.datafeed.gtfrankfurter.url};
   *                   empty as long as no such instance is operated
   */
  public GtFrankfurterFeedConnector(@Value("${gt.datafeed.gtfrankfurter.url:}") String apiRootUrl) {
    super("gtfrankfurter", "Frankfurter (GT instance)", apiRootUrl);
  }
}

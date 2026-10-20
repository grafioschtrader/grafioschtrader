package grafioschtrader.connector.fundamental;

import java.time.Duration;

import grafioschtrader.entities.Security;
import grafioschtrader.types.FundamentalSource;

/**
 * A source of fundamental base values of direct equities, asked once a week by the task {@code FUNDAMENTAL_DATA_LOAD}.
 * Finnhub and Eulerpool implement it in their price connectors, because only those can read their API key; Yahoo has a
 * connector of its own.
 */
public interface IFundamentalFeedConnector {

  FundamentalSource getFundamentalSource();

  /** True when the source can be asked at all: its key is present and the connector is activated. */
  boolean isFundamentalAvailable();

  /** True when the source can identify the security, for example by its ISIN or a US ticker. */
  boolean supportsFundamentals(Security security);

  /**
   * Asks the source for the base values of one security.
   *
   * @param security a supported security
   * @return the rows of a usable answer, or the reason why the answer could not be used
   * @throws Exception on a network failure
   */
  FundamentalAnswer loadFundamentals(Security security) throws Exception;

  /** Pause between two requests, chosen to stay inside the free rate limit of the source. */
  Duration getFundamentalPause();
}

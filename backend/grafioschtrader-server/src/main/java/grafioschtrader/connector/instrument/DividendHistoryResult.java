package grafioschtrader.connector.instrument;

import java.util.List;

import grafioschtrader.entities.Dividend;
import grafioschtrader.types.DistributionFrequency;

/**
 * Dividend history of an instrument together with the distribution frequency the data provider states itself, when it
 * does. Both come from the same response, so a caller that needs the frequency does not send a second request.
 *
 * @param dividends         the dividends, sorted ascending by ex-date as {@link IFeedConnector#getDividendHistory}
 *                          returns them
 * @param providerFrequency frequency as stated by the provider, e.g. DivvyDiary's {@code dividendFrequency}; null when
 *                          the provider states none or a value without GT counterpart
 */
public record DividendHistoryResult(List<Dividend> dividends, DistributionFrequency providerFrequency) {

  public DividendHistoryResult {
    dividends = dividends == null ? List.of() : dividends;
  }
}

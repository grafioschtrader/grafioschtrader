package grafioschtrader.instrument;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.repository.SecurityActionJpaRepository;

/**
 * Brings the quotes a history connector delivers for a spin-off parent into the basis GT stores: adjusted for the
 * real splits in {@code securitysplit}, unadjusted for every recorded spin-off. A source that divided the parent's
 * prices before an action date by the history factor of that spin-off gets them multiplied back:
 *
 * <pre>
 * g(d) = product of historyFactor(a) over the spin-offs a with history basis ADJUSTED and actionDate(a) &gt; d
 * close x g(d); open, high, low x g(d) unless the connector stores them as traded; volume unchanged
 * </pre>
 *
 * The method must be applied exactly once per delivered quote, at the place where connector quotes enter GT.
 */
@Component
public class SpinOffProviderAdjustment {

  private final SecurityActionJpaRepository securityActionJpaRepository;

  public SpinOffProviderAdjustment(SecurityActionJpaRepository securityActionJpaRepository) {
    this.securityActionJpaRepository = securityActionJpaRepository;
  }

  /**
   * Multiplies the delivered quotes of a spin-off parent back to the stored basis, in place.
   *
   * @param security  the security the quotes were delivered for
   * @param quotes    the delivered quotes; changed in place and returned
   * @param connector the history connector that delivered them, decides whether open, high and low are scaled
   * @return the same list
   */
  public List<Historyquote> revert(Security security, List<Historyquote> quotes, IFeedConnector connector) {
    if (security == null || security.getIdSecuritycurrency() == null || quotes == null || quotes.isEmpty()) {
      return quotes;
    }
    List<SecurityAction> spinOffs = securityActionJpaRepository.findSpinOffsOfParent(security.getIdSecuritycurrency());
    revert(spinOffs, quotes, connector != null && connector.isHistoryOpenHighLowAsTraded());
    return quotes;
  }

  /**
   * Pure form of {@link #revert(Security, List, IFeedConnector)}.
   *
   * @param spinOffs        the spin-offs of the parent
   * @param quotes          the delivered quotes; changed in place
   * @param ohlAsTraded     true when open, high and low are as traded and stay unchanged
   */
  static void revert(List<SecurityAction> spinOffs, List<Historyquote> quotes, boolean ohlAsTraded) {
    if (spinOffs.stream().allMatch(a -> a.getAppliedHistoryFactor() == 1.0)) {
      return;
    }
    for (Historyquote quote : quotes) {
      double g = factorBefore(spinOffs, quote.getDate());
      if (g != 1.0) {
        quote.setClose(quote.getClose() * g);
        if (!ohlAsTraded) {
          quote.setOpen(scale(quote.getOpen(), g));
          quote.setHigh(scale(quote.getHigh(), g));
          quote.setLow(scale(quote.getLow(), g));
        }
      }
    }
  }

  /**
   * Product of the applied history factors of the spin-offs dated after the given day.
   *
   * @param spinOffs the spin-offs of the parent
   * @param date     the quote date
   * @return the factor, 1 when no adjusted spin-off follows the date
   */
  public static double factorBefore(List<SecurityAction> spinOffs, LocalDate date) {
    double g = 1.0;
    for (SecurityAction spinOff : spinOffs) {
      if (spinOff.getActionDate().isAfter(date)) {
        g *= spinOff.getAppliedHistoryFactor();
      }
    }
    return g;
  }

  private static Double scale(Double value, double factor) {
    return value == null ? null : value * factor;
  }
}

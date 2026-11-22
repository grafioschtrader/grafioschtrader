package grafioschtrader.instrument;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.types.SpinOffHistoryBasis;
import grafioschtrader.types.SpinOffHistoryBasisOrigin;

/**
 * Decides for one recorded spin-off whether the series a history source delivers for the parent is adjusted for it,
 * and by which factor. The decision belongs to that series and that event, never to the identity of history and split
 * connector: one connector delivers some events adjusted and others not, and a factor reported by the split connector
 * was observed on another series. The rules are tried in order:
 * <ol>
 * <li>a setting of the administrator for the current source that has not yet been applied;</li>
 * <li>the reference: the ratio of the stored to the delivered close before and after the action date, available when
 * the stored quotes are known to be unadjusted for the event;</li>
 * <li>an applied setting of the administrator for the current source;</li>
 * <li>the step of the delivered close on the action date compared with the provider split factor;</li>
 * <li>otherwise undetermined.</li>
 * </ol>
 */
public final class SpinOffHistoryBasisDecider {

  /** Days before and after the action date read for the reference and the step. */
  public static final int WINDOW_DAYS = 10;
  /** Minimum number of common dates in each reference window. */
  static final int MIN_DATES = 3;
  /** Maximum relative distance of a reference ratio from the median of its window. */
  static final double MAX_SCATTER = 0.005;
  /** Below this absolute log ratio the delivered series is taken as unadjusted for the event. */
  static final double SAME_BASIS = 0.005;
  /** Minimum market move the rejected reading of the step must imply. */
  static final double STEP_MIN_MOVE = Math.log(1.10);
  /** Factor by which the rejected reading must exceed the accepted one. */
  static final double STEP_RATIO = 3.0;

  private SpinOffHistoryBasisDecider() {
  }

  /**
   * Everything the decision for one spin-off reads.
   *
   * @param spinOff                    the spin-off with its current history basis
   * @param connector                  the parent's current history connector
   * @param urlExtend                  the parent's current history URL extension
   * @param historySourceIsSplitSource true when history connector and URL equal split connector and URL
   * @param storedUnadjusted           true when the stored quotes before the action date are unadjusted for it
   * @param storedLoadedBeforeAction   true when the parent's last full load is dated before the action date
   * @param sameDaySplitFactor         product of the factors of the parent's stored splits dated on the action date
   * @param stored                     the stored quotes of the window
   * @param delivered                  the delivered quotes of the window, before any revert
   */
  public record Input(SecurityAction spinOff, String connector, String urlExtend, boolean historySourceIsSplitSource,
      boolean storedUnadjusted, boolean storedLoadedBeforeAction, double sameDaySplitFactor, List<Historyquote> stored,
      List<Historyquote> delivered) {
  }

  /**
   * The outcome for one spin-off.
   *
   * @param basis  the history basis
   * @param factor the history factor, set only for {@link SpinOffHistoryBasis#ADJUSTED}
   * @param origin how it was decided, null when undetermined
   */
  public record Decision(SpinOffHistoryBasis basis, Double factor, SpinOffHistoryBasisOrigin origin) {
    static final Decision UNDETERMINED = new Decision(SpinOffHistoryBasis.UNDETERMINED, null, null);
  }

  /**
   * Applies the rules in order and returns the first decision.
   *
   * @param in the inputs of one spin-off
   * @return the decision, never null
   */
  public static Decision decide(Input in) {
    SecurityAction a = in.spinOff();
    boolean adminForSource = a.getHistoryBasisOrigin() == SpinOffHistoryBasisOrigin.ADMINISTRATOR
        && a.getHistoryBasis() != SpinOffHistoryBasis.UNDETERMINED
        && Objects.equals(a.getHistoryConnector(), in.connector())
        && Objects.equals(a.getHistoryUrlExtend(), in.urlExtend());
    Decision admin = adminForSource ? new Decision(a.getHistoryBasis(),
        a.getHistoryBasis() == SpinOffHistoryBasis.ADJUSTED ? a.getHistoryFactor() : null,
        SpinOffHistoryBasisOrigin.ADMINISTRATOR) : null;
    if (admin != null && !a.isHistoryBasisApplied()) {
      return admin;
    }
    if (in.storedUnadjusted()) {
      Optional<Decision> reference = reference(in);
      if (reference.isPresent()) {
        return reference.get();
      }
    }
    if (admin != null) {
      return admin;
    }
    return step(in).orElse(Decision.UNDETERMINED);
  }

  /**
   * Measures the factor by which the delivered series is adjusted for the event relative to the stored one:
   *
   * <pre>
   * r(d) = stored close(d) / delivered close(d)
   * phi  = median(r before the action date) / median(r from the action date on)
   * </pre>
   *
   * Adjustments that change both windows alike, such as a dividend adjustment of an adjusted close or a later split,
   * cancel in the quotient. When the stored quotes were loaded before the action date, a real split on that date is in
   * the delivered series but not in the stored one and is divided out.
   */
  static Optional<Decision> reference(Input in) {
    LocalDate date = in.spinOff().getActionDate();
    Map<LocalDate, Double> stored = new HashMap<>();
    in.stored().stream().filter(q -> valid(q.getClose())).forEach(q -> stored.put(q.getDate(), q.getClose()));
    List<Double> before = new ArrayList<>();
    List<Double> after = new ArrayList<>();
    for (Historyquote quote : in.delivered()) {
      Double storedClose = stored.get(quote.getDate());
      if (storedClose != null && valid(quote.getClose()) && inWindow(quote.getDate(), date)) {
        (quote.getDate().isBefore(date) ? before : after).add(storedClose / quote.getClose());
      }
    }
    OptionalDouble medianBefore = consistentMedian(before);
    OptionalDouble medianAfter = consistentMedian(after);
    if (medianBefore.isEmpty() || medianAfter.isEmpty()) {
      return Optional.empty();
    }
    double phi = medianBefore.getAsDouble() / medianAfter.getAsDouble()
        / (in.storedLoadedBeforeAction() ? in.sameDaySplitFactor() : 1.0);
    double logPhi = Math.log(phi);
    if (Math.abs(logPhi) <= SAME_BASIS) {
      return Optional.of(new Decision(SpinOffHistoryBasis.UNADJUSTED, null, SpinOffHistoryBasisOrigin.REFERENCE));
    }
    return logPhi > SAME_BASIS
        ? Optional.of(new Decision(SpinOffHistoryBasis.ADJUSTED, phi, SpinOffHistoryBasisOrigin.REFERENCE))
        : Optional.empty();
  }

  /**
   * Reads the step of the delivered close on the action date. With the provider split factor f the market move of the
   * action date is x when the series is adjusted and x + ln f when it is unadjusted. A reading is accepted only when
   * the rejected one implies a move of at least 10 % and three times the accepted one. An adjusted reading yields a
   * factor only when the provider split was reported for the same series.
   */
  static Optional<Decision> step(Input in) {
    Double providerFactor = in.spinOff().getProviderSplitFactor();
    OptionalDouble x = stepMove(in.delivered(), in.spinOff().getActionDate());
    if (providerFactor == null || providerFactor <= 0 || x.isEmpty()) {
      return Optional.empty();
    }
    double mAdj = x.getAsDouble();
    double mRaw = mAdj + Math.log(providerFactor);
    if (Math.abs(mAdj) >= STEP_MIN_MOVE && Math.abs(mRaw) <= Math.abs(mAdj) / STEP_RATIO) {
      return Optional.of(new Decision(SpinOffHistoryBasis.UNADJUSTED, null, SpinOffHistoryBasisOrigin.STEP));
    }
    double spinOffFactor = providerFactor / in.sameDaySplitFactor();
    if (Math.abs(mRaw) >= STEP_MIN_MOVE && Math.abs(mAdj) <= Math.abs(mRaw) / STEP_RATIO
        && in.historySourceIsSplitSource() && spinOffFactor > 1.0) {
      return Optional.of(new Decision(SpinOffHistoryBasis.ADJUSTED, spinOffFactor, SpinOffHistoryBasisOrigin.STEP));
    }
    return Optional.empty();
  }

  /**
   * Log return of the close from the last quote before the action date to the quote on it.
   *
   * @param series the quotes around the action date
   * @param date   the action date
   * @return x = ln(close(date) / close(last date before)), empty when either quote is missing
   */
  public static OptionalDouble stepMove(List<Historyquote> series, LocalDate date) {
    Optional<Historyquote> onDate = series.stream().filter(q -> q.getDate().equals(date) && valid(q.getClose()))
        .findFirst();
    Optional<Historyquote> before = series.stream()
        .filter(q -> q.getDate().isBefore(date) && !q.getDate().isBefore(date.minusDays(WINDOW_DAYS))
            && valid(q.getClose()))
        .max(Comparator.comparing(Historyquote::getDate));
    return onDate.isPresent() && before.isPresent()
        ? OptionalDouble.of(Math.log(onDate.get().getClose() / before.get().getClose()))
        : OptionalDouble.empty();
  }

  private static OptionalDouble consistentMedian(List<Double> ratios) {
    if (ratios.size() < MIN_DATES) {
      return OptionalDouble.empty();
    }
    List<Double> sorted = ratios.stream().sorted().toList();
    int n = sorted.size();
    double median = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    boolean consistent = sorted.stream().allMatch(r -> Math.abs(r / median - 1.0) <= MAX_SCATTER);
    return consistent ? OptionalDouble.of(median) : OptionalDouble.empty();
  }

  private static boolean inWindow(LocalDate quoteDate, LocalDate actionDate) {
    return !quoteDate.isBefore(actionDate.minusDays(WINDOW_DAYS))
        && !quoteDate.isAfter(actionDate.plusDays(WINDOW_DAYS));
  }

  private static boolean valid(Double close) {
    return close != null && close > 0 && Double.isFinite(close);
  }
}

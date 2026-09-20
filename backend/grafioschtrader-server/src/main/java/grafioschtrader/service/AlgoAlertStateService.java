package grafioschtrader.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoAlertState;
import grafioschtrader.repository.AlgoAlertStateJpaRepository;

/**
 * Turns a level into a crossing.
 *
 * <p>
 * An alert is specified to notify when a price, a moving average relation or an RSI moves across a bound, and to stay
 * quiet while it remains on the side it is already on. That needs the previous side, and it needs it to survive a
 * restart, which is why it is a table row rather than a field on the evaluator. Without it the evaluation degenerates
 * into a level test: an instrument whose price is above its upper bound reports an alarm on the first evaluation and on
 * every one after, and a moving average alert says "the price is above its average" rather than "the price has just
 * crossed its average".
 * </p>
 *
 * <p>
 * Each bound of an alert is tracked separately - an absolute price alert has a lower and an upper one, and they cross
 * independently - and each instrument is tracked separately, because one strategy on an AlgoTop or an asset class
 * bucket is evaluated against every instrument below it.
 * </p>
 */
@Service
public class AlgoAlertStateService {

  /** Bound key of the lower threshold of an absolute price alert. */
  public static final String BOUND_LOWER = "LOWER";

  /** Bound key of the upper threshold of an absolute price alert. */
  public static final String BOUND_UPPER = "UPPER";

  /** Bound key of the moving average of an MA crossing alert. */
  public static final String BOUND_MA = "MA";

  /** Bound key of the oversold threshold of an RSI alert. */
  public static final String BOUND_RSI_LOWER = "RSI_LOWER";

  /** Bound key of the overbought threshold of an RSI alert. */
  public static final String BOUND_RSI_UPPER = "RSI_UPPER";

  @Autowired
  private AlgoAlertStateJpaRepository algoAlertStateJpaRepository;

  /**
   * Records where an observed value stands relative to a bound and reports what changed since the previous observation.
   *
   * <p>
   * The state is written whether or not the caller ends up sending a notification. Daily deduplication may swallow the
   * message, but the side the instrument is on is a fact about the market and has to be carried forward, or the next
   * evaluation would see a crossing that has already been reported.
   * </p>
   *
   * <p>
   * Shares the scope evaluation transaction so a crossing baseline and its durable signal commit together. If recording
   * fails, the baseline rolls back and the crossing remains eligible on the next evaluation.
   * </p>
   *
   * @param idTenant           owning tenant
   * @param idAlgoStrategy     the alert being evaluated
   * @param idSecuritycurrency the instrument it is evaluated against
   * @param boundKey           which bound of the alert, one of the BOUND_ constants
   * @param fingerprint        identity of the configuration, from
   *                           {@link grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter#fingerprint}
   * @param observed           the value just measured, for example the last price or the RSI
   * @param bound              the threshold it is measured against
   * @return what this observation amounted to
   */
  @Transactional
  public AlgoCrossingResult observe(Integer idTenant, Integer idAlgoStrategy, Integer idSecuritycurrency,
      String boundKey, String fingerprint, double observed, double bound) {
    byte side = sideOf(observed, bound);
    Optional<AlgoAlertState> stored = algoAlertStateJpaRepository
        .findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyAndBoundKey(idTenant, idAlgoStrategy, idSecuritycurrency,
            boundKey);

    if (stored.isEmpty()) {
      algoAlertStateJpaRepository.save(new AlgoAlertState(idTenant, idAlgoStrategy, idSecuritycurrency, boundKey,
          fingerprint, side, observed, LocalDateTime.now()));
      return AlgoCrossingResult.BASELINE_ESTABLISHED;
    }

    AlgoAlertState state = stored.get();
    byte previousSide = state.getLastSide();
    boolean sameConfiguration = fingerprint.equals(state.getConfigFingerprint());
    state.setConfigFingerprint(fingerprint);
    state.setLastSide(side);
    state.setLastValue(observed);
    state.setLastEvaluated(LocalDateTime.now());
    algoAlertStateJpaRepository.save(state);

    return classify(previousSide, side, sameConfiguration);
  }

  /**
   * What a move from one side of a bound to another amounts to.
   *
   * <p>
   * An upward crossing moves from at or below the bound to above it, a downward crossing from at or above it to below
   * it. Everything else, a move onto the bound exactly included, leaves the value on a side it has already been
   * reported from: touching the bound from below is not yet above it.
   * </p>
   *
   * @param previousSide      the side recorded at the previous evaluation
   * @param side              the side just observed
   * @param sameConfiguration whether both were observed under the same configuration. When they were not, the stored
   *                          side describes a bound that no longer exists in that form and can say nothing about a
   *                          crossing of the bound now in force
   * @return the classification
   */
  static AlgoCrossingResult classify(byte previousSide, byte side, boolean sameConfiguration) {
    if (!sameConfiguration) {
      return AlgoCrossingResult.BASELINE_ESTABLISHED;
    }
    if (previousSide <= 0 && side == 1) {
      return AlgoCrossingResult.CROSSED_UP;
    }
    if (previousSide >= 0 && side == -1) {
      return AlgoCrossingResult.CROSSED_DOWN;
    }
    return AlgoCrossingResult.NO_CHANGE;
  }

  /**
   * Discards every baseline of one alert. Called for a pair whose strategy or whose enclosing node is switched off, so
   * that the next evaluation after it is switched on again starts from a fresh baseline instead of reporting the move
   * that happened while nobody was watching.
   *
   * @param idAlgoStrategy     the alert whose baselines are dropped
   * @param idSecuritycurrency the instrument they were held for
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void discard(Integer idAlgoStrategy, Integer idSecuritycurrency) {
    algoAlertStateJpaRepository.deleteByIdAlgoStrategyAndIdSecuritycurrency(idAlgoStrategy, idSecuritycurrency);
  }

  /**
   * Which side of a bound a value is on.
   *
   * @param observed the measured value
   * @param bound    the threshold
   * @return -1 below, 0 exactly at it, 1 above
   */
  static byte sideOf(double observed, double bound) {
    int comparison = Double.compare(observed, bound);
    return (byte) Integer.signum(comparison);
  }

}

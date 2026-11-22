package grafioschtrader.instrument;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.SpinOffProviderSplitsDto;
import grafioschtrader.dto.SpinOffProviderSplitsDto.ProviderSplit;
import grafioschtrader.dto.SpinOffProviderSplitsDto.Step;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.instrument.SpinOffHistoryBasisDecider.Decision;
import grafioschtrader.instrument.SpinOffHistoryBasisDecider.Input;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityActionJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.SpinOffHistoryBasis;

/**
 * Decides the history basis of every recorded spin-off of a parent before a full reload of its history. It must run
 * before the stored quotes are deleted, because the stored series serves as reference, and in the transaction of the
 * reload, so that a failed reload rolls the decision back. The reload that follows stores the delivered quotes through
 * {@link SpinOffProviderAdjustment}, which applies the decision.
 */
@Service
public class SpinOffHistoryBasisService {

  private static final Logger log = LoggerFactory.getLogger(SpinOffHistoryBasisService.class);

  private final SecurityActionJpaRepository securityActionJpaRepository;
  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final SecuritysplitJpaRepository securitysplitJpaRepository;
  private final SecurityJpaRepository securityJpaRepository;

  @Autowired(required = false)
  private List<IFeedConnector> feedConnectors = new ArrayList<>();

  public SpinOffHistoryBasisService(SecurityActionJpaRepository securityActionJpaRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository, SecuritysplitJpaRepository securitysplitJpaRepository,
      SecurityJpaRepository securityJpaRepository) {
    this.securityActionJpaRepository = securityActionJpaRepository;
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.securitysplitJpaRepository = securitysplitJpaRepository;
    this.securityJpaRepository = securityJpaRepository;
  }

  /**
   * Collects what the administrator needs to judge the provider split and the history basis of a spin-off of the
   * parent on the given date, recorded or not yet recorded. Reads the split connector and the history connector of the
   * parent, so it takes as long as two connector requests.
   *
   * @param idSecurityOld the parent security
   * @param actionDate    the action date
   * @return the provider splits around the date, the recorded history basis and the price steps
   */
  @Transactional(readOnly = true)
  public SpinOffProviderSplitsDto describe(Integer idSecurityOld, LocalDate actionDate) {
    Security parent = idSecurityOld == null ? null : securityJpaRepository.findById(idSecurityOld).orElse(null);
    if (parent == null || actionDate == null) {
      throw new DataViolationException("security.old", "gt.security.action.spinoff.parent.invalid", null);
    }
    SecurityAction spinOff = securityActionJpaRepository.findSpinOffsOfParent(idSecurityOld).stream()
        .filter(a -> a.getActionDate().equals(actionDate)).findFirst().orElse(null);
    List<Securitysplit> stored = securitysplitJpaRepository.findByIdSecuritycurrencyOrderBySplitDateAsc(idSecurityOld);
    double sameDaySplitFactor = sameDaySplitFactor(stored, actionDate);
    List<ProviderSplit> splits = new ArrayList<>();
    boolean splitsUnavailable = readProviderSplits(parent, actionDate, splits);
    Double providerFactor = splits.stream().filter(ProviderSplit::dropped).map(ProviderSplit::factor).findFirst()
        .orElse(spinOff == null ? null : spinOff.getProviderSplitFactor());
    Double spinOffFactor = providerFactor == null ? null : providerFactor / sameDaySplitFactor;
    List<Historyquote> storedQuotes = historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(
        idSecurityOld, actionDate.minusDays(SpinOffHistoryBasisDecider.WINDOW_DAYS),
        actionDate.plusDays(SpinOffHistoryBasisDecider.WINDOW_DAYS));
    boolean currentSource = spinOff != null
        && Objects.equals(spinOff.getHistoryConnector(), parent.getIdConnectorHistory())
        && Objects.equals(spinOff.getHistoryUrlExtend(), parent.getUrlHistoryExtend());
    return new SpinOffProviderSplitsDto(splits, splitsUnavailable, providerFactor, spinOffFactor, sameSource(parent),
        spinOff == null ? null : spinOff.getHistoryBasis(), spinOff == null ? null : spinOff.getHistoryFactor(),
        spinOff == null ? null : spinOff.getHistoryBasisOrigin(),
        spinOff == null ? null : spinOff.getHistoryConnector(), spinOff == null ? null : spinOff.getHistoryUrlExtend(),
        currentSource, step(storedQuotes, actionDate, providerFactor),
        step(readDelivered(parent, actionDate), actionDate, providerFactor));
  }

  /** Reads the connector splits around the date into the list and returns true when the connector failed. */
  private boolean readProviderSplits(Security parent, LocalDate actionDate, List<ProviderSplit> splits) {
    IFeedConnector connector = parent.getIdConnectorSplit() == null ? null
        : ConnectorHelper.getConnectorByConnectorId(feedConnectors, parent.getIdConnectorSplit(), FeedSupport.FS_SPLIT);
    if (connector == null) {
      return parent.getIdConnectorSplit() != null;
    }
    LocalDate from = actionDate.minusDays(SpinOffHistoryBasisDecider.WINDOW_DAYS);
    LocalDate to = actionDate.plusDays(SpinOffHistoryBasisDecider.WINDOW_DAYS);
    try {
      for (Securitysplit split : connector.getSplitHistory(parent, from, to)) {
        if (!split.getSplitDate().isBefore(from) && !split.getSplitDate().isAfter(to)) {
          splits.add(new ProviderSplit(split.getSplitDate(), split.getFromFactor(), split.getToFactor(),
              split.getFactor(), split.getSplitDate().equals(actionDate)));
        }
      }
      splits.sort(Comparator.comparing(ProviderSplit::splitDate));
      return false;
    } catch (Exception e) {
      log.warn("Splits of security {} not readable: {}", parent.getIdSecuritycurrency(), e.getMessage());
      return true;
    }
  }

  private static Step step(List<Historyquote> series, LocalDate actionDate, Double providerFactor) {
    OptionalDouble x = SpinOffHistoryBasisDecider.stepMove(series, actionDate);
    if (x.isEmpty()) {
      return null;
    }
    double value = x.getAsDouble();
    Double mRaw = providerFactor == null || providerFactor <= 0 ? null : value + Math.log(providerFactor);
    return new Step(value, value, mRaw);
  }

  private static double sameDaySplitFactor(List<Securitysplit> splits, LocalDate date) {
    double factor = 1.0;
    for (Securitysplit split : splits) {
      if (split.getSplitDate().equals(date)) {
        factor *= split.getFactor();
      }
    }
    return factor;
  }

  private static boolean sameSource(Security parent) {
    return Objects.equals(parent.getIdConnectorHistory(), parent.getIdConnectorSplit())
        && Objects.equals(parent.getUrlHistoryExtend(), parent.getUrlSplitExtend());
  }

  /**
   * Decides and stores the history basis of each spin-off of the parent. Does nothing for a security without spin-offs
   * or without an available history connector.
   *
   * @param parent the security whose history is about to be reloaded in full
   */
  @Transactional
  public void decide(Security parent) {
    if (parent == null || parent.getIdSecuritycurrency() == null || parent.getIdConnectorHistory() == null) {
      return;
    }
    List<SecurityAction> spinOffs = securityActionJpaRepository.findSpinOffsOfParent(parent.getIdSecuritycurrency());
    if (spinOffs.isEmpty()) {
      return;
    }
    IFeedConnector connector = historyConnector(parent);
    if (connector == null) {
      return;
    }
    List<Securitysplit> splits = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(parent.getIdSecuritycurrency());
    for (SecurityAction spinOff : spinOffs) {
      Decision decision = SpinOffHistoryBasisDecider.decide(input(parent, spinOff, connector, splits));
      spinOff.setHistoryBasis(decision.basis());
      spinOff.setHistoryFactor(decision.factor());
      spinOff.setHistoryBasisOrigin(decision.origin());
      spinOff.setHistoryConnector(parent.getIdConnectorHistory());
      spinOff.setHistoryUrlExtend(parent.getUrlHistoryExtend());
      spinOff.setHistoryBasisApplied(decision.basis() != SpinOffHistoryBasis.UNDETERMINED);
      securityActionJpaRepository.save(spinOff);
      log.info("Spin-off {} of security {}: history basis {} factor {} ({})", spinOff.getIdSecurityAction(),
          parent.getIdSecuritycurrency(), decision.basis(), decision.factor(), decision.origin());
    }
  }

  /**
   * Reads the delivered quotes of the window around an action date directly from the history connector, without the
   * revert of {@link SpinOffProviderAdjustment}.
   *
   * @param parent     the parent security
   * @param actionDate the action date
   * @return the delivered quotes, empty when the connector is unavailable or fails
   */
  public List<Historyquote> readDelivered(Security parent, LocalDate actionDate) {
    IFeedConnector connector = historyConnector(parent);
    return connector == null ? List.of() : readDelivered(connector, parent, actionDate);
  }

  private Input input(Security parent, SecurityAction spinOff, IFeedConnector connector, List<Securitysplit> splits) {
    LocalDate date = spinOff.getActionDate();
    boolean loadedBefore = parent.getFullLoadTimestamp() != null
        && parent.getFullLoadTimestamp().toLocalDate().isBefore(date);
    double sameDaySplitFactor = sameDaySplitFactor(splits, date);
    boolean sameSource = sameSource(parent);
    List<Historyquote> stored = historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(
        parent.getIdSecuritycurrency(), date.minusDays(SpinOffHistoryBasisDecider.WINDOW_DAYS),
        date.plusDays(SpinOffHistoryBasisDecider.WINDOW_DAYS));
    return new Input(spinOff, parent.getIdConnectorHistory(), parent.getUrlHistoryExtend(), sameSource,
        spinOff.isHistoryBasisApplied() || loadedBefore, loadedBefore, sameDaySplitFactor, stored,
        readDelivered(connector, parent, date));
  }

  private List<Historyquote> readDelivered(IFeedConnector connector, Security parent, LocalDate date) {
    try {
      List<Historyquote> delivered = connector.getEodSecurityHistory(parent,
          date.minusDays(SpinOffHistoryBasisDecider.WINDOW_DAYS),
          date.plusDays(SpinOffHistoryBasisDecider.WINDOW_DAYS));
      return delivered == null ? List.of() : delivered;
    } catch (Exception e) {
      log.warn("History of security {} around {} not readable for the spin-off history basis: {}",
          parent.getIdSecuritycurrency(), date, e.getMessage());
      return List.of();
    }
  }

  private IFeedConnector historyConnector(Security parent) {
    return ConnectorHelper.getConnectorByConnectorId(feedConnectors, parent.getIdConnectorHistory(),
        FeedSupport.FS_HISTORY);
  }
}

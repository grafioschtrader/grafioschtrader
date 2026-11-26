package grafioschtrader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import grafiosch.common.ClientClock;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.UserJpaRepository;
import grafiosch.service.SendMailInternalExternalService;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.SpinOffAllocationProposalDto;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.instrument.lookup.SuccessorSecurityProposer;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityActionApplicationJpaRepository;
import grafioschtrader.repository.SecurityActionJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SecurityActionType;
import grafioschtrader.types.SpinOffHistoryBasis;
import grafioschtrader.types.SpinOffHistoryBasisOrigin;
import grafioschtrader.validation.ISINValidator;
import grafioschtrader.validation.ValidCurrencyCodeValidator;

/** Records spin-offs and derives allocation proposals from historical closes. */
@Service
public class SpinOffActionService {
  private static final Logger LOG = LoggerFactory.getLogger(SpinOffActionService.class);
  private static final SpinOffAllocationProposalDto NO_PROPOSAL = new SpinOffAllocationProposalDto(null, null, null,
      null, null);

  private final SecurityActionJpaRepository actions;
  private final SecurityActionApplicationJpaRepository applications;
  private final SecurityJpaRepository securities;
  private final TransactionJpaRepository transactions;
  private final HistoryquoteJpaRepository quotes;
  private final SecuritysplitJpaRepository splits;
  private final CurrencypairJpaRepository currencyPairs;
  private final UserJpaRepository users;
  private final SendMailInternalExternalService mail;
  private final SuccessorSecurityProposer proposer;
  private final SpinOffParentRefreshService parentRefresh;

  public SpinOffActionService(SecurityActionJpaRepository actions, SecurityActionApplicationJpaRepository applications,
      SecurityJpaRepository securities, TransactionJpaRepository transactions, HistoryquoteJpaRepository quotes,
      SecuritysplitJpaRepository splits, CurrencypairJpaRepository currencyPairs, UserJpaRepository users,
      SendMailInternalExternalService mail, SuccessorSecurityProposer proposer,
      SpinOffParentRefreshService parentRefresh) {
    this.actions = actions;
    this.applications = applications;
    this.securities = securities;
    this.transactions = transactions;
    this.quotes = quotes;
    this.splits = splits;
    this.currencyPairs = currencyPairs;
    this.users = users;
    this.mail = mail;
    this.proposer = proposer;
    this.parentRefresh = parentRefresh;
  }

  /**
   * Resolves an existing child or proposes it from the lookup before entering the recording transaction. A child
   * selected by instrument id needs no lookup.
   */
  public Security previewChild(Integer parentId, String isin, String currency, LocalDate actionDate, Locale locale) {
    Security parent = findParent(parentId);
    requireActionDate(actionDate);
    if (!StringUtils.hasText(isin) || !new ISINValidator().isValid(isin, null)
        || !new ValidCurrencyCodeValidator().isValid(currency, null)) {
      throw missingChild();
    }
    String currencyCode = currency.strip().toUpperCase(Locale.ROOT);
    Security existing = securities.findByIsinAndCurrency(isin, currencyCode);
    return validateChild(parent,
        existing != null ? existing
            : proposer.proposeSpinOffChild(parent, isin, currencyCode, actionDate, locale)
                .orElseThrow(SpinOffActionService::missingChild));
  }

  /** Records the action and, when necessary, saves the instrument proposed by the server-side lookup. */
  @Transactional(rollbackFor = Exception.class)
  public SecurityAction record(SecurityAction action, Security childProposal, User admin) throws Exception {
    Integer parentId = idOf(action.getSecurityOld());
    // Lock the parent even when the duplicate check has no action row to lock yet.
    Security parent = validateParent(
        parentId == null ? null : securities.findByIdForSecurityAction(parentId).orElse(null));
    requireActionDate(action.getActionDate());
    requireRatio(action.getFromFactor(), action.getToFactor());
    validateAllocation(action);
    if (actions.findFirstBySecurityOld_IdSecuritycurrencyAndActionDateAndActionType(parentId, action.getActionDate(),
        SecurityActionType.SPIN_OFF.getValue()).isPresent()) {
      throw new DataViolationException("action.date", "gt.security.action.spinoff.duplicate", null);
    }
    Integer childId = idOf(action.getSecurityNew());
    Security child = childId == null ? null
        : securities.findById(childId).orElseThrow(SpinOffActionService::missingChild);
    if (child == null && childProposal != null) {
      child = securities.findByIsinAndCurrency(childProposal.getIsin(), childProposal.getCurrency());
      if (child == null) {
        child = securities.saveOnlyAttributes(validateChild(parent, childProposal), null, Set.of());
      }
    }
    child = validateChild(parent, child);

    Set<Integer> tenants = affectedTenants(parentId, action.getActionDate());
    SecurityAction stored = new SecurityAction();
    stored.setActionType(SecurityActionType.SPIN_OFF);
    stored.setSecurityOld(parent);
    stored.setSecurityNew(child);
    stored.setIsinOld(parent.getIsin());
    stored.setIsinNew(child.getIsin());
    stored.setActionDate(action.getActionDate());
    stored.setFromFactor(action.getFromFactor());
    stored.setToFactor(action.getToFactor());
    stored.setCreatedBy(admin.getIdUser());
    stored.setCreationTime(LocalDateTime.now().withNano(0));
    stored.setAffectedCount(tenants.size());
    stored.setHistoryBasis(SpinOffHistoryBasis.UNDETERMINED);
    copyAllocation(action, stored, action.getNote());
    SecurityAction saved = actions.save(stored);
    notifyTenants(saved, admin.getIdUser(), tenants);
    parentRefresh.enqueue(parentId);
    return saved;
  }

  /**
   * Returns the allocation using the first day in the eleven-day window on which the parent and the child have a close
   * and the exchange rate from the child's to the parent's currency is known.
   */
  @Transactional(readOnly = true)
  public SpinOffAllocationProposalDto proposeAllocation(Integer parentId, Integer childId, LocalDate actionDate,
      Integer fromFactor, Integer toFactor) {
    Security parent = findParent(parentId);
    Security child = validateChild(parent, securities.findById(childId).orElse(null));
    requireActionDate(actionDate);
    requireRatio(fromFactor, toFactor);
    requireLaterHistoryDetermined(actions, parentId, childId, actionDate);
    LocalDate endDate = actionDate.plusDays(10);
    Map<LocalDate, Double> childCloses = new HashMap<>();
    for (Historyquote quote : quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(childId, actionDate, endDate)) {
      if (positive(quote.getClose())) {
        childCloses.put(quote.getDate(), quote.getClose());
      }
    }
    for (Historyquote quote : quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(parentId, actionDate, endDate)) {
      Double childClose = childCloses.get(quote.getDate());
      if (childClose != null && positive(quote.getClose())) {
        Double fx = childToParentRate(child.getCurrency(), parent.getCurrency(), quote.getDate());
        if (fx == null) {
          // A missing exchange rate on this day does not rule out a later common day of the window.
          continue;
        }
        double parentPrice = quote.getClose() * splitFactor(parentId, actionDate);
        double childPrice = childClose * splitFactor(childId, actionDate);
        double childValue = (double) toFactor / fromFactor * childPrice * fx;
        return new SpinOffAllocationProposalDto(quote.getDate(), parentPrice, childPrice, fx,
            childValue / (parentPrice + childValue));
      }
    }
    return NO_PROPOSAL;
  }

  /**
   * Refuses pricing the legs of a spin-off while a later spin-off of its parent or child has not decided whether the
   * history source is adjusted for it. Such a source may have divided the closes from the action date on, which the
   * legs and the allocation proposal read.
   *
   * @param actions    the action repository
   * @param parentId   the parent of the spin-off
   * @param childId    the child of the spin-off
   * @param actionDate the action date of the spin-off
   */
  static void requireLaterHistoryDetermined(SecurityActionJpaRepository actions, Integer parentId, Integer childId,
      LocalDate actionDate) {
    if (actions.existsLaterSpinOffWithUndeterminedHistory(List.of(parentId, childId), actionDate)) {
      throw new DataViolationException("action.date", "gt.security.action.spinoff.history.undetermined", null);
    }
  }

  /**
   * Enforces the immutable identity of the action, corrects the allocation of all active applications and takes over a
   * history basis set by the administrator. A changed history basis enqueues the parent refresh, which reloads the
   * parent's history under the new basis.
   */
  @Transactional
  public SecurityAction correct(Integer id, SecurityAction request) {
    SecurityAction stored = actions.findByIdForUpdate(id)
        .orElseThrow(() -> new DataViolationException("id.security.action", "gt.security.action.not.found", null));
    if (stored.getActionType() != SecurityActionType.SPIN_OFF || !sameIdentity(stored, request)) {
      throw new DataViolationException("id.security.action", "gt.security.action.immutable", null);
    }
    validateAllocation(request);
    String note = request.getNote();
    String allocationChange = describeAllocationChange(stored, request);
    if (allocationChange != null) {
      note = StringUtils.hasText(note) ? note + "\n" + allocationChange : allocationChange;
    }
    String historyChange = takeOverHistoryBasis(stored, request);
    if (historyChange != null) {
      note = StringUtils.hasText(note) ? note + "\n" + historyChange : historyChange;
    }
    validateNote(note);
    copyAllocation(request, stored, note);
    applications.findBySecurityAction_IdSecurityActionAndReversedFalse(id)
        .forEach(application -> application.setCostAllocation(stored.getCostAllocation()));
    if (historyChange != null) {
      parentRefresh.enqueue(idOf(stored.getSecurityOld()));
    }
    return stored;
  }

  /**
   * Takes over the history basis of the request. A request with origin {@code ADMINISTRATOR} sets the basis, which must
   * be {@code UNADJUSTED}, or {@code ADJUSTED} with a factor above 1; a request with another origin while the stored
   * basis was set by the administrator returns it to the automatic decision. Any other request leaves the basis alone.
   *
   * @return the note line describing the change, null when nothing changed
   */
  private static String takeOverHistoryBasis(SecurityAction stored, SecurityAction request) {
    boolean requestAdmin = request.getHistoryBasisOrigin() == SpinOffHistoryBasisOrigin.ADMINISTRATOR;
    boolean storedAdmin = stored.getHistoryBasisOrigin() == SpinOffHistoryBasisOrigin.ADMINISTRATOR;
    String before = describeHistoryBasis(stored);
    if (requestAdmin) {
      SpinOffHistoryBasis basis = request.getHistoryBasis();
      Double factor = basis == SpinOffHistoryBasis.ADJUSTED ? request.getHistoryFactor() : null;
      boolean valid = basis == SpinOffHistoryBasis.UNADJUSTED
          || basis == SpinOffHistoryBasis.ADJUSTED && factor != null && factor > 1.0 && Double.isFinite(factor);
      if (!valid) {
        throw new DataViolationException("history.factor", "gt.security.action.spinoff.history.factor", null);
      }
      if (storedAdmin && stored.getHistoryBasis() == basis && Objects.equals(stored.getHistoryFactor(), factor)) {
        return null;
      }
      Security parent = stored.getSecurityOld();
      stored.setHistoryBasis(basis);
      stored.setHistoryFactor(factor);
      stored.setHistoryBasisOrigin(SpinOffHistoryBasisOrigin.ADMINISTRATOR);
      stored.setHistoryConnector(parent.getIdConnectorHistory());
      stored.setHistoryUrlExtend(parent.getUrlHistoryExtend());
      stored.setHistoryBasisApplied(false);
    } else if (storedAdmin) {
      stored.setHistoryBasis(SpinOffHistoryBasis.UNDETERMINED);
      stored.setHistoryFactor(null);
      stored.setHistoryBasisOrigin(null);
      stored.setHistoryBasisApplied(false);
    } else {
      return null;
    }
    return "%s: history %s -> %s".formatted(ClientClock.today(), before, describeHistoryBasis(stored));
  }

  /**
   * Describes the changed allocation fields as one note line. The source is not repeated, because it is stored in its
   * own field and would fill the note after a few corrections.
   *
   * @return the note line, null when no allocation field changed
   */
  private static String describeAllocationChange(SecurityAction stored, SecurityAction request) {
    List<String> changes = new ArrayList<>();
    if (!Objects.equals(stored.getCostAllocation(), request.getCostAllocation())) {
      changes.add("allocation %s -> %s".formatted(stored.getCostAllocation(), request.getCostAllocation()));
    }
    if (stored.getTaxTreatmentDe() != request.getTaxTreatmentDe()) {
      changes.add("DE %s -> %s".formatted(stored.getTaxTreatmentDe(), request.getTaxTreatmentDe()));
    }
    if (!Objects.equals(stored.getCostAllocationDe(), request.getCostAllocationDe())) {
      changes.add("DE allocation %s -> %s".formatted(stored.getCostAllocationDe(), request.getCostAllocationDe()));
    }
    if (!Objects.equals(stored.getCostAllocationSource(), request.getCostAllocationSource())) {
      changes.add("source changed");
    }
    return changes.isEmpty() ? null : ClientClock.today() + ": " + String.join(", ", changes);
  }

  private static String describeHistoryBasis(SecurityAction action) {
    String basis = Objects.toString(action.getHistoryBasis(), SpinOffHistoryBasis.UNDETERMINED.name());
    return action.getHistoryFactor() == null ? basis : basis + " " + action.getHistoryFactor();
  }

  private static boolean sameIdentity(SecurityAction stored, SecurityAction request) {
    return Objects.equals(stored.getIdSecurityAction(), request.getIdSecurityAction())
        && stored.getActionType() == request.getActionType()
        && Objects.equals(idOf(stored.getSecurityOld()), idOf(request.getSecurityOld()))
        && Objects.equals(idOf(stored.getSecurityNew()), idOf(request.getSecurityNew()))
        && Objects.equals(stored.getIsinOld(), request.getIsinOld())
        && Objects.equals(stored.getIsinNew(), request.getIsinNew())
        && Objects.equals(stored.getActionDate(), request.getActionDate())
        && Objects.equals(stored.getFromFactor(), request.getFromFactor())
        && Objects.equals(stored.getToFactor(), request.getToFactor())
        && Objects.equals(stored.getCreatedBy(), request.getCreatedBy())
        && Objects.equals(stored.getCreationTime(), request.getCreationTime())
        && stored.getAffectedCount() == request.getAffectedCount()
        && stored.getAppliedCount() == request.getAppliedCount();
  }

  private static void copyAllocation(SecurityAction source, SecurityAction target, String note) {
    target.setCostAllocation(source.getCostAllocation());
    target.setCostAllocationSource(source.getCostAllocationSource());
    target.setTaxTreatmentDe(source.getTaxTreatmentDe());
    target.setCostAllocationDe(source.getCostAllocationDe());
    target.setNote(note);
  }

  private static void validateAllocation(SecurityAction action) {
    if (!isAllocation(action.getCostAllocation())) {
      throw new DataViolationException("cost.allocation", "gt.security.action.spinoff.allocation.invalid", null);
    }
    if (action.getCostAllocationDe() != null && !isAllocation(action.getCostAllocationDe())) {
      throw new DataViolationException("cost.allocation.de", "gt.security.action.spinoff.allocation.invalid", null);
    }
    if (action.getTaxTreatmentDe() == null) {
      throw new DataViolationException("tax.treatment.de", "gt.security.action.spinoff.tax.required", null);
    }
    if (action.getCostAllocationSource() != null && action.getCostAllocationSource().length() > 255) {
      throw new DataViolationException("cost.allocation.source", "gt.security.action.spinoff.source.too.long", null);
    }
    validateNote(action.getNote());
  }

  private static void validateNote(String note) {
    if (note != null && note.length() > 1024) {
      throw new DataViolationException("note", "gt.security.action.spinoff.note.too.long", null);
    }
  }

  private static void requireActionDate(LocalDate date) {
    if (date == null) {
      throw new DataViolationException("action.date", "gt.security.action.spinoff.date.required", null);
    }
  }

  private static void requireRatio(Integer from, Integer to) {
    if (from == null || to == null || from <= 0 || to <= 0) {
      throw new DataViolationException("from.factor", "gt.security.action.spinoff.ratio.required", null);
    }
  }

  private static boolean isAllocation(Double value) {
    return value != null && value > 0 && value < 1;
  }

  private static boolean positive(Double value) {
    return value != null && value > 0 && Double.isFinite(value);
  }

  private Security findParent(Integer id) {
    return validateParent(id == null ? null : securities.findById(id).orElse(null));
  }

  private static Security validateParent(Security parent) {
    if (!isEquity(parent) || parent.isMarginInstrument()) {
      throw new DataViolationException("security.old", "gt.security.action.spinoff.parent.invalid", null);
    }
    return parent;
  }

  private static Security validateChild(Security parent, Security child) {
    if (child == null) {
      throw missingChild();
    }
    if (!isEquity(child) || Objects.equals(idOf(parent), idOf(child))) {
      throw new DataViolationException("security.new", "gt.security.action.spinoff.child.invalid", null);
    }
    return child;
  }

  private static boolean isEquity(Security security) {
    return security != null && StringUtils.hasText(security.getIsin()) && security.getAssetClass() != null
        && security.getAssetClass().getCategoryType() == AssetclassType.EQUITIES;
  }

  private static Integer idOf(Security security) {
    return security == null ? null : security.getIdSecuritycurrency();
  }

  private static DataViolationException missingChild() {
    return new DataViolationException("security.new", "gt.security.action.spinoff.child.missing", null);
  }

  private double splitFactor(Integer id, LocalDate actionDate) {
    return Securitysplit.calcSplitFatorForFromDate(splits.findByIdSecuritycurrencyOrderBySplitDateAsc(id), actionDate);
  }

  private Double childToParentRate(String child, String parent, LocalDate date) {
    if (child.equals(parent)) {
      return 1.0;
    }
    for (var pair : currencyPairs.findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(child, parent)) {
      Double close = currencyPairs.getHistoricalClosePriceForDate(pair, date);
      if (positive(close)) {
        return DataBusinessHelper.divideMultiplyExchangeRate(1, close, child, parent,
            !child.equals(pair.getFromCurrency()));
      }
    }
    return null;
  }

  private record Account(Integer tenant, Integer securityAccount) {
  }

  private Set<Integer> affectedTenants(Integer parentId, LocalDate actionDate) {
    List<Securitysplit> parentSplits = splits.findByIdSecuritycurrencyOrderBySplitDateAsc(parentId);
    double actionFactor = Securitysplit.calcSplitFatorForFromDate(parentSplits, actionDate);
    Map<Account, Double> units = new HashMap<>();
    for (var trade : transactions.findBySecurity_idSecuritycurrencyAndTransactionTimeBefore(parentId,
        actionDate.atStartOfDay())) {
      int sign = switch (trade.getTransactionType()) {
      case ACCUMULATE -> 1;
      case REDUCE -> -1;
      default -> 0;
      };
      if (sign != 0 && trade.getIdSecurityaccount() != null) {
        double factor = Securitysplit.calcSplitFatorForFromDate(parentSplits, trade.getTransactionDate());
        units.merge(new Account(trade.getIdTenant(), trade.getIdSecurityaccount()),
            sign * trade.getUnits() * factor / actionFactor, Double::sum);
      }
    }
    return units.entrySet().stream().filter(entry -> DataBusinessHelper.round(entry.getValue()) > 0)
        .map(entry -> entry.getKey().tenant()).collect(Collectors.toSet());
  }

  private void notifyTenants(SecurityAction action, Integer adminId, Set<Integer> tenants) {
    String subject = "Spin-off: " + action.getIsinOld() + " -> " + action.getIsinNew();
    String body = "Spin-off on %s. Parent units : child units = %d : %d.".formatted(action.getActionDate(),
        action.getFromFactor(), action.getToFactor());
    for (Integer tenant : tenants) {
      try {
        users.findByIdTenant(tenant).ifPresent(user -> mail.sendInternalMail(adminId, user.getIdUser(), subject, body));
      } catch (RuntimeException e) {
        LOG.warn("Could not send spin-off notification to tenant {}", tenant, e);
      }
    }
  }
}

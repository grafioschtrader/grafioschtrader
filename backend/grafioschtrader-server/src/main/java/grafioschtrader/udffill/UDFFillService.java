package grafioschtrader.udffill;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.TaskInterruptException;
import grafiosch.repository.UserJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.UDFDataType;
import grafioschtrader.dto.UDFFillInfo;
import grafioschtrader.dto.UDFFillStatus;
import grafioschtrader.dto.UDFFillTestRequest;
import grafioschtrader.dto.UDFFillTestResult;
import grafioschtrader.dto.UDFFillTestResult.UDFFillMismatch;
import grafioschtrader.dto.UDFFillTestResult.UDFFillSample;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFDataFill;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reports.udfalluserfields.AllUserFieldsSecurity;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup.IUDFEntityValues;
import grafioschtrader.repository.UDFDataFillJpaRepository;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.UDFFillPeriod;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.UDFFillTemplate.TemplateResult;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs, tests and summarises the automatic filling of user-defined security fields, and carries out the cell actions of
 * the user. Every write goes through {@link UDFFillWriter}; the outcome of a source is always computed outside a
 * transaction.
 */
@Service
public class UDFFillService {

  private static final Logger log = LoggerFactory.getLogger(UDFFillService.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final UDFMetadataSecurityJpaRepository uMetaSecurityRepository;
  private final UDFDataFillJpaRepository udfDataFillJpaRepository;
  private final UserJpaRepository userJpaRepository;
  private final UDFFillScopeService scopeService;
  private final UDFFillWriter udfFillWriter;
  private final UDFFillTaskQueue udfFillTaskQueue;
  private final GlobalparametersService globalparametersService;

  public UDFFillService(UDFMetadataSecurityJpaRepository uMetaSecurityRepository,
      UDFDataFillJpaRepository udfDataFillJpaRepository, UserJpaRepository userJpaRepository,
      UDFFillScopeService scopeService, UDFFillWriter udfFillWriter, UDFFillTaskQueue udfFillTaskQueue,
      GlobalparametersService globalparametersService) {
    this.uMetaSecurityRepository = uMetaSecurityRepository;
    this.udfDataFillJpaRepository = udfDataFillJpaRepository;
    this.userJpaRepository = userJpaRepository;
    this.scopeService = scopeService;
    this.udfFillWriter = udfFillWriter;
    this.udfFillTaskQueue = udfFillTaskQueue;
    this.globalparametersService = globalparametersService;
  }

  /**
   * One fill run for one field, the work of the task {@code UDF_FILL_VALUES} with a field as entity. Every due cell of
   * the tenant scope is computed and written in its own transaction; afterwards the fill rows of securities that left
   * the scope are removed. Between two securities an interruption ends the run, and the next run continues with the
   * cells still due.
   *
   * @param idUDFMetadata the field
   * @throws TaskInterruptException when the run was interrupted, for instance by its timeout
   */
  public void runField(Integer idUDFMetadata) {
    UDFMetadataSecurity field = idUDFMetadata == null ? null
        : uMetaSecurityRepository.findById(idUDFMetadata).orElse(null);
    if (field == null || !isUserFillable(field) || !field.isAutomaticallyFilled()) {
      return;
    }
    User owner = userJpaRepository.findById(field.getIdUser()).orElse(null);
    if (owner == null || !canCompute(field)) {
      return;
    }
    List<Security> scope = scopeService.getTenantScope(field, owner.getIdTenant());
    Map<Integer, UDFDataFill> fills = udfDataFillJpaRepository.findById_IdUDFMetadata(idUDFMetadata).stream()
        .collect(Collectors.toMap(f -> f.getId().getIdEntity(), Function.identity()));
    Map<Integer, String> yahooSymbols = scopeService.getYahooSymbols();
    Map<UDFFillWriteResult, Integer> counts = new EnumMap<>(UDFFillWriteResult.class);
    LocalDateTime refreshBefore = refreshBefore(field.getFillPeriod(), LocalDateTime.now(ZoneOffset.UTC),
        globalparametersService.getUdfFillResolverRefreshDays());
    for (Security security : scope) {
      if (Thread.interrupted()) {
        throw new TaskInterruptException(new InterruptedException());
      }
      if (isDue(field, fills.get(security.getIdSecuritycurrency()), refreshBefore)) {
        fillOne(field, security, yahooSymbols, counts);
      }
    }
    removeOutOfScope(field, fills.keySet(), scope);
    log.info("Fill run of user field {} with {} securities in scope: {}", idUDFMetadata, scope.size(), counts);
  }

  /**
   * Tells whether anything fills a field: a manual field is always typed by the user, a template or a lookup needs a
   * rule it can evaluate, a language model needs a model of the home tenant of the owner.
   *
   * @param field          the field
   * @param tenantHasModel whether the home tenant of the owner has an active, permitted language model
   * @return true when the field is processed
   */
  public boolean isProcessed(UDFMetadataSecurity field, boolean tenantHasModel) {
    return switch (field.getFillSource()) {
    case MANUAL -> true;
    case TEMPLATE, RESOLVER -> canCompute(field);
    case LLM -> tenantHasModel;
    };
  }

  private boolean canCompute(UDFMetadataSecurity field) {
    return scopeService.canCompute(field);
  }

  private void fillOne(UDFMetadataSecurity field, Security security, Map<Integer, String> yahooSymbols,
      Map<UDFFillWriteResult, Integer> counts) {
    Integer idSecurity = security.getIdSecuritycurrency();
    try {
      UDFFillOutcome outcome = scopeService.computeOutcome(field, security, yahooSymbols.get(idSecurity));
      counts.merge(udfFillWriter.write(field, idSecurity, outcome), 1, Integer::sum);
    } catch (TaskInterruptException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Automatic fill of user field {} for security {} failed", field.getIdUDFMetadata(), idSecurity, e);
    }
  }

  /**
   * Tells whether a cell is due: it has no fill row, or its row was produced by an earlier rule. Otherwise a template,
   * whose evaluation is local, is always due, and a resolver when its APPLIED or NOT_FOUND row was checked before
   * {@code refreshBefore}. A locked cell is never due.
   *
   * @param field         the field
   * @param fill          the fill row of the cell, or null
   * @param refreshBefore a resolver row checked before this UTC time is looked up again; null never looks it up again
   * @return true when the cell is computed in this run
   */
  static boolean isDue(UDFMetadataSecurity field, UDFDataFill fill, LocalDateTime refreshBefore) {
    if (fill == null) {
      return true;
    }
    if (fill.getFillState() == UDFFillState.LOCKED) {
      return false;
    }
    if (fill.getRuleVersion() != field.getFillRuleVersion()) {
      return true;
    }
    return switch (field.getFillSource()) {
    case TEMPLATE -> true;
    case RESOLVER -> refreshBefore != null
        && (fill.getFillState() == UDFFillState.APPLIED || fill.getFillState() == UDFFillState.NOT_FOUND)
        && fill.getCheckedTime().isBefore(refreshBefore);
    default -> false;
    };
  }

  /**
   * The time before which an automatically filled or not found cell is checked again: the period of the field, or the
   * global number of days for a field without period.
   *
   * @param period      the fill period of the field, may be null
   * @param now         the current UTC time
   * @param defaultDays the global refresh days, used without period
   * @return the time, or null when the period is once and a checked cell is never checked again
   */
  public static LocalDateTime refreshBefore(UDFFillPeriod period, LocalDateTime now, int defaultDays) {
    if (period == null) {
      return now.minusDays(defaultDays);
    }
    return period.refreshes() ? now.minusMonths(period.getValue()) : null;
  }

  private void removeOutOfScope(UDFMetadataSecurity field, Set<Integer> filledIds, List<Security> scope) {
    Set<Integer> scopeIds = scope.stream().map(Security::getIdSecuritycurrency).collect(Collectors.toSet());
    for (Integer idSecurity : filledIds) {
      if (!scopeIds.contains(idSecurity)) {
        try {
          udfFillWriter.removeOutOfScope(field, idSecurity);
        } catch (Exception e) {
          log.warn("Removing fill row of user field {} for security {} failed", field.getIdUDFMetadata(), idSecurity,
              e);
        }
      }
    }
  }

  /**
   * Tells whether a field may have a fill source other than manual: it belongs to a user other than 0 and has no
   * special type.
   */
  public static boolean isUserFillable(UDFMetadataSecurity field) {
    return field.getIdUser() != null && field.getIdUser() != BaseConstants.UDF_ID_USER
        && field.getUdfSpecialTypeAsByte() == null;
  }

  /**
   * Evaluates a draft rule against the tenant scope of the caller and compares it with the stored values of the field.
   * Nothing is written.
   *
   * @param request the draft
   * @param user    the caller
   * @return the counters and up to 20 mismatches and samples each
   */
  public UDFFillTestResult testFillRule(UDFFillTestRequest request, User user) {
    Map<Integer, String> existing = Map.of();
    if (request.idUDFMetadata != null) {
      getOwnField(request.idUDFMetadata, user);
      existing = scopeService.getFieldValues(user.getIdUser(), request.idUDFMetadata);
    }
    if (request.fillSource != UDFFillSource.TEMPLATE) {
      throw new DataViolationException("fill.source", "gt.udf.fill.source.not.testable", null);
    }
    UDFMetadataSecurity draft = createDraft(request);
    UDFFillTemplate template = parseForSave(request.fillRule);
    UDFFillTestResult result = new UDFFillTestResult();
    List<UDFFillMismatch> mismatches = new ArrayList<>();
    List<UDFFillSample> samples = new ArrayList<>();
    Map<Integer, String> yahooSymbols = scopeService.getYahooSymbols();
    for (Security security : scopeService.getTenantScope(draft, user.getIdTenant())) {
      Integer id = security.getIdSecuritycurrency();
      TemplateResult tr = template.evaluate(UDFFillContext.of(security, yahooSymbols.get(id)), draft);
      countTestResult(result, security, existing.get(id), tr, mismatches, samples);
    }
    Comparator<String> byName = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    result.mismatches = mismatches.stream().sorted(Comparator.comparing(UDFFillMismatch::name, byName))
        .limit(UDFFillTestResult.MAX_LIST_ENTRIES).toList();
    result.samples = samples.stream().sorted(Comparator.comparing(UDFFillSample::name, byName))
        .limit(UDFFillTestResult.MAX_LIST_ENTRIES).toList();
    return result;
  }

  private void countTestResult(UDFFillTestResult result, Security security, String existing, TemplateResult tr,
      List<UDFFillMismatch> mismatches, List<UDFFillSample> samples) {
    result.inScope++;
    boolean hasExisting = !UDFFillDecision.isEmpty(existing);
    if (hasExisting) {
      result.withValue++;
    }
    String reason = null;
    if (tr.hasValue()) {
      result.produced++;
    } else if (tr.isInvalid()) {
      result.invalid++;
      reason = tr.getInvalidMessage();
    } else {
      result.noLine++;
      reason = describeFallThrough(tr);
    }
    if (hasExisting) {
      if (existing.equals(tr.value())) {
        result.equal++;
      } else {
        result.differing++;
        mismatches.add(new UDFFillMismatch(security.getName(), existing, tr.value(), reason));
      }
    } else if (tr.hasValue()) {
      samples.add(new UDFFillSample(security.getName(), tr.value()));
    }
  }

  private static String describeFallThrough(TemplateResult tr) {
    return tr.fallThrough().stream()
        .map(lo -> lo.lineNumber() + ": " + lo.reason() + (lo.detail() == null ? "" : " " + lo.detail()))
        .collect(Collectors.joining("; "));
  }

  private UDFMetadataSecurity createDraft(UDFFillTestRequest request) {
    return createDraft(request, UDFFillSource.TEMPLATE, UDFFillSources.TEMPLATE_DATA_TYPES);
  }

  /**
   * Builds an unsaved field from the draft of the edit form, so a test sees the form and not the stored field.
   *
   * @param request    the draft
   * @param fillSource the fill source the test is for
   * @param dataTypes  the data types this fill source can fill
   * @return the transient field
   * @throws DataViolationException when the data type of the draft does not fit the fill source
   */
  public static UDFMetadataSecurity createDraft(UDFFillTestRequest request, UDFFillSource fillSource,
      Set<UDFDataType> dataTypes) {
    UDFMetadataSecurity draft = new UDFMetadataSecurity();
    draft.setUdfDataType(request.udfDataType);
    draft.setFieldSize(request.fieldSize);
    if (request.categoryTypeEnums != null) {
      draft.setCategoryTypeEnums(request.categoryTypeEnums);
    }
    if (request.specialInvestmentInstrumentEnums != null) {
      draft.setSpecialInvestmentInstrumentEnums(request.specialInvestmentInstrumentEnums);
    }
    draft.setFillSource(request.fillSource);
    draft.setFillRule(request.fillRule);
    if (request.udfDataType == null || !dataTypes.contains(request.udfDataType)) {
      throw new DataViolationException("fill.source", "gt.udf.fill.source.data.type",
          new Object[] { fillSource.name(), request.udfDataType });
    }
    return draft;
  }

  /**
   * Parses a rule for saving or testing and turns a parse error into a translated validation error.
   *
   * @param rule the rule
   * @return the parsed template
   * @throws DataViolationException naming line and position of the fault
   */
  public static UDFFillTemplate parseForSave(String rule) {
    try {
      return UDFFillTemplate.parse(rule);
    } catch (UDFFillTemplateException e) {
      throw new DataViolationException("fill.rule", e.getReason().getNlsKey(), e.getMessageArgs());
    }
  }

  /**
   * Summarises the fill state of a field of the caller over the tenant scope.
   *
   * @param idUDFMetadata the field
   * @param user          the caller, who must own the field
   * @return the counts per state, the manual values and whether a run is pending
   */
  public UDFFillStatus getFillStatus(Integer idUDFMetadata, User user) {
    UDFMetadataSecurity field = getOwnField(idUDFMetadata, user);
    List<Security> scope = scopeService.getTenantScope(field, user.getIdTenant());
    Map<Integer, UDFDataFill> fills = udfDataFillJpaRepository.findById_IdUDFMetadata(idUDFMetadata).stream()
        .collect(Collectors.toMap(f -> f.getId().getIdEntity(), Function.identity()));
    Map<Integer, String> values = scopeService.getFieldValues(user.getIdUser(), idUDFMetadata);
    UDFFillStatus status = new UDFFillStatus();
    status.inScope = scope.size();
    for (Security security : scope) {
      UDFDataFill fill = fills.get(security.getIdSecuritycurrency());
      String cur = values.get(security.getIdSecuritycurrency());
      boolean editedAutomatic = fill != null && fill.getFillState() == UDFFillState.APPLIED
          && !fill.getFilledValue().equals(cur);
      if (fill != null && !editedAutomatic) {
        status.countByState.merge(fill.getFillState(), 1, Integer::sum);
      } else if (!UDFFillDecision.isEmpty(cur)) {
        status.manual++;
      }
    }
    status.waiting = udfFillTaskQueue.isWaiting(idUDFMetadata);
    status.running = udfFillTaskQueue.isRunning(idUDFMetadata);
    return status;
  }

  /**
   * The cell action "Take over manually": nothing automatic touches the cell any more, whether it holds a value or not.
   *
   * @param idUDFMetadata the field of the caller
   * @param idSecurity    a security of the tenant scope
   * @param user          the caller
   */
  public void lockCell(Integer idUDFMetadata, Integer idSecurity, User user) {
    UDFMetadataSecurity field = getOwnFilledFieldInScope(idUDFMetadata, idSecurity, user);
    udfFillWriter.lock(field, idSecurity);
  }

  /**
   * The cell action "Fill automatically again": deletes the lock and, for a template, evaluates the cell at once. A
   * resolver makes HTTP requests, which never run on a request thread; its field gets a fill run instead.
   *
   * @param idUDFMetadata the field of the caller
   * @param idSecurity    a security of the tenant scope
   * @param user          the caller
   */
  public void unlockCell(Integer idUDFMetadata, Integer idSecurity, User user) {
    UDFMetadataSecurity field = getOwnFilledFieldInScope(idUDFMetadata, idSecurity, user);
    udfFillWriter.deleteFillRow(field, idSecurity);
    if (field.getFillSource() == UDFFillSource.TEMPLATE && canCompute(field)) {
      Security security = scopeService.getTenantScope(field, user.getIdTenant()).stream()
          .filter(s -> s.getIdSecuritycurrency().equals(idSecurity)).findFirst().get();
      UDFFillOutcome outcome = scopeService.computeOutcome(field, security,
          scopeService.getYahooSymbols().get(idSecurity));
      udfFillWriter.write(field, idSecurity, outcome);
    } else if (field.getFillSource() == UDFFillSource.RESOLVER) {
      udfFillTaskQueue.enqueue(field.getIdUDFMetadata(), TaskDataExecPriority.PRIO_NORMAL);
    }
  }

  private UDFMetadataSecurity getOwnFilledFieldInScope(Integer idUDFMetadata, Integer idSecurity, User user) {
    UDFMetadataSecurity field = getOwnField(idUDFMetadata, user);
    if (!field.isAutomaticallyFilled()) {
      throw new DataViolationException("fill.source", "gt.udf.fill.source.manual", null);
    }
    boolean inScope = scopeService.getTenantScope(field, user.getIdTenant()).stream()
        .anyMatch(s -> s.getIdSecuritycurrency().equals(idSecurity));
    if (!inScope) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return field;
  }

  private UDFMetadataSecurity getOwnField(Integer idUDFMetadata, User user) {
    UDFMetadataSecurity field = uMetaSecurityRepository.findById(idUDFMetadata).orElse(null);
    if (field == null || !user.getIdUser().equals(field.getIdUser())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return field;
  }

  /**
   * Fills the template fields of the user for the securities of a watchlist, synchronously while the watchlist is
   * loaded. Only in the home tenant of the user with write access; the fill rows and the Yahoo symbols are loaded once
   * for the whole watchlist, and a write transaction is opened only for a cell whose decision changes something.
   *
   * @param user           the user who loads the watchlist
   * @param securities     the securities of the watchlist
   * @param valuesSupplier reads per security the current values of the user; called only when the user has a template
   *                       field
   */
  public void fillWatchlistTemplates(User user, List<Security> securities,
      Supplier<Map<Integer, Map<String, Object>>> valuesSupplier) {
    if (!user.getIdTenant().equals(user.getActualIdTenant()) || user.isTenantAccessReadOnly() || securities.isEmpty()) {
      return;
    }
    List<UDFMetadataSecurity> fields = uMetaSecurityRepository
        .getAllByIdUserInAndUiOrderLessThanOrderByUiOrder(new int[] { user.getIdUser() },
            BaseConstants.MAX_USER_UI_ORDER_VALUE)
        .stream().filter(f -> f.getFillSource() == UDFFillSource.TEMPLATE && isUserFillable(f) && canCompute(f))
        .toList();
    if (fields.isEmpty()) {
      return;
    }
    Set<Integer> ids = securities.stream().map(Security::getIdSecuritycurrency).collect(Collectors.toSet());
    Map<String, UDFDataFill> fills = udfDataFillJpaRepository.getByIdUserAndIdEntityIn(user.getIdUser(), ids).stream()
        .collect(
            Collectors.toMap(f -> f.getId().getIdUDFMetadata() + "_" + f.getId().getIdEntity(), Function.identity()));
    Map<Integer, String> yahooSymbols = scopeService.getYahooSymbols();
    Map<Integer, Map<String, Object>> values = valuesSupplier.get();
    for (UDFMetadataSecurity field : fields) {
      for (Security security : securities) {
        if (security.tenantHasAccess(user.getIdTenant())
            && AllUserFieldsSecurity.matchAssetclassAndSpecialInvestmentInstruments(field, security.getAssetClass())) {
          fillWatchlistCell(field, security, fills, yahooSymbols, values);
        }
      }
    }
  }

  private void fillWatchlistCell(UDFMetadataSecurity field, Security security, Map<String, UDFDataFill> fills,
      Map<Integer, String> yahooSymbols, Map<Integer, Map<String, Object>> values) {
    Integer idSecurity = security.getIdSecuritycurrency();
    try {
      UDFDataFill fill = fills.get(field.getIdUDFMetadata() + "_" + idSecurity);
      Map<String, Object> securityValues = values.get(idSecurity);
      Object curObj = securityValues == null ? null
          : securityValues.get(BaseConstants.UDF_FIELD_PREFIX + field.getIdUDFMetadata());
      UDFFillOutcome outcome = scopeService.computeOutcome(field, security, yahooSymbols.get(idSecurity));
      UDFFillDecision decision = UDFFillDecision.decide(fill == null ? null : fill.getFillState(),
          fill == null ? null : fill.getFilledValue(), outcome, curObj == null ? null : curObj.toString(), true);
      if (decision.changesSomething(fill == null ? null : fill.getRuleVersion(), field.getFillRuleVersion())) {
        udfFillWriter.write(field, idSecurity, outcome);
      }
    } catch (Exception e) {
      log.warn("Automatic fill of user field {} for security {} on watchlist load failed", field.getIdUDFMetadata(),
          idSecurity, e);
    }
  }

  /**
   * Returns the fill marks of the cells of the user for the given securities: an automatic cell whose fill row is
   * APPLIED and still holds the current value, and a locked cell.
   *
   * @param user   the user
   * @param ids    the securities
   * @param values per security the current values of the user
   * @return per security ID and field key the fill mark
   */
  public Map<Integer, Map<String, UDFFillInfo>> getFillInfo(User user, Set<Integer> ids,
      Map<Integer, Map<String, Object>> values) {
    Map<Integer, Map<String, UDFFillInfo>> fillInfo = new HashMap<>();
    if (ids.isEmpty()) {
      return fillInfo;
    }
    for (UDFDataFill fill : udfDataFillJpaRepository.getByIdUserAndIdEntityIn(user.getIdUser(), ids)) {
      Integer idSecurity = fill.getId().getIdEntity();
      String key = BaseConstants.UDF_FIELD_PREFIX + fill.getId().getIdUDFMetadata();
      Map<String, Object> securityValues = values.get(idSecurity);
      Object cur = securityValues == null ? null : securityValues.get(key);
      boolean automatic = fill.getFillState() == UDFFillState.APPLIED && cur != null
          && cur.toString().equals(fill.getFilledValue());
      if (fill.getFillState() == UDFFillState.PROPOSED && (cur == null || cur.toString().isEmpty())) {
        // A proposal is shown apart from the values and never inside them
        fillInfo.computeIfAbsent(idSecurity, _ -> new HashMap<>()).put(key,
            new UDFFillInfo(fill.getFillSource(), fill.getFillState(), fill.getCheckedTime(), fill.getSourceUrl(),
                fill.getFilledValue(), fill.getEvidence(), fill.getRuleVersion()));
      } else if (automatic || fill.getFillState() == UDFFillState.LOCKED) {
        fillInfo.computeIfAbsent(idSecurity, _ -> new HashMap<>()).put(key,
            new UDFFillInfo(fill.getFillSource(), fill.getFillState(), fill.getCheckedTime(), fill.getSourceUrl()));
      }
    }
    return fillInfo;
  }

  /**
   * Parses the JSON documents of the user-defined values as read for the watchlist.
   *
   * @param entityValues per instrument the JSON document of the values
   * @return per instrument ID the values by field key; a document that does not parse is skipped
   */
  public static Map<Integer, Map<String, Object>> parseValues(List<IUDFEntityValues> entityValues) {
    Map<Integer, Map<String, Object>> values = new HashMap<>();
    for (IUDFEntityValues ev : entityValues) {
      if (ev.getJsonValues() != null) {
        try {
          values.put(ev.getIdSecuritycurrency(), OBJECT_MAPPER.readValue(ev.getJsonValues(), MAP_TYPE));
        } catch (JacksonException e) {
          log.warn("User-defined values of instrument {} are not valid JSON", ev.getIdSecuritycurrency());
        }
      }
    }
    return values;
  }

  /**
   * Collects the IDs of the given securities.
   */
  public static Set<Integer> idsOf(List<Security> securities) {
    return new HashSet<>(securities.stream().map(Security::getIdSecuritycurrency).toList());
  }
}

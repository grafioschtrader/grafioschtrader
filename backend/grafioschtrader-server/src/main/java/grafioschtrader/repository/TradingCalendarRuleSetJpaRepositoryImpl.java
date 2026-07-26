package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.calendar.rule.HolidayRuleSet;
import grafioschtrader.calendar.rule.HolidayRuleSetResolver;
import grafioschtrader.calendar.rule.HolidayRuleSetYamlParser;
import grafioschtrader.entities.TradingCalendarRuleSet;
import grafioschtrader.repository.TradingCalendarRuleSetJpaRepository.IdRuleSetUsage;
import grafioschtrader.service.TradingCalendarRuleYamlValidator;
import grafioschtrader.types.TaskTypeExtended;

public class TradingCalendarRuleSetJpaRepositoryImpl extends BaseRepositoryImpl<TradingCalendarRuleSet>
    implements TradingCalendarRuleSetJpaRepositoryCustom {

  @Autowired
  private TradingCalendarRuleSetJpaRepository tradingCalendarRuleSetJpaRepository;

  @Autowired
  private TradingCalendarRuleYamlValidator tradingCalendarRuleYamlValidator;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public TradingCalendarRuleSet saveOnlyAttributes(TradingCalendarRuleSet ruleSet,
      TradingCalendarRuleSet existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    validateRuleSet(ruleSet, existingEntity);
    // Read the previous closure-affecting fields before the save overwrites the existing entity.
    String oldRuleYaml = existingEntity == null ? null : existingEntity.getRuleYaml();
    Integer oldExtends = existingEntity == null ? null : existingEntity.getIdExtendsRuleSet();

    TradingCalendarRuleSet saved = RepositoryHelper.saveOnlyAttributes(tradingCalendarRuleSetJpaRepository, ruleSet,
        existingEntity, updatePropertyLevelClasses);

    enqueueCalendarRebuildOnRuleChange(saved, existingEntity != null, oldRuleYaml, oldExtends);
    return saved;
  }

  /**
   * Schedules a trading calendar rebuild when a rule change alters the closures the set produces. Only an update to an
   * existing set matters: a newly created set has no exchange using it yet. The single job rebuilds every exchange
   * that uses this set or a set that extends it, so one change never fans out into many jobs.
   */
  private void enqueueCalendarRebuildOnRuleChange(TradingCalendarRuleSet saved, boolean isUpdate, String oldRuleYaml,
      Integer oldExtends) {
    if (!isUpdate) {
      return;
    }
    boolean closureAffectingChange = !Objects.equals(oldRuleYaml, saved.getRuleYaml())
        || !Objects.equals(oldExtends, saved.getIdExtendsRuleSet());
    if (!closureAffectingChange) {
      return;
    }
    Integer idRuleSet = saved.getIdTradingCalendarRuleSet();
    if (taskDataChangeJpaRepository
        .findByIdTaskAndIdEntityAndProgressStateType(
            TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET.getValue(), idRuleSet,
            ProgressStateType.PROG_WAITING.getValue())
        .isEmpty()) {
      taskDataChangeJpaRepository.save(new TaskDataChange(
          TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET, TaskDataExecPriority.PRIO_LOW,
          LocalDateTime.now(), idRuleSet, TradingCalendarRuleSet.class.getSimpleName()));
    }
  }

  /**
   * Rejects a rule set that cannot produce a usable calendar. This runs on every persisting path, not only on the REST
   * update, because a broken rule set would otherwise surface much later as a silently wrong trading calendar.
   */
  private void validateRuleSet(TradingCalendarRuleSet ruleSet, TradingCalendarRuleSet existingEntity) {
    List<String> yamlErrors = tradingCalendarRuleYamlValidator.validate(ruleSet.getRuleYaml());
    if (!yamlErrors.isEmpty()) {
      throw new DataViolationException("rule.yaml", "gt.calendar.rule.yaml.invalid",
          new Object[] { String.join("; ", yamlErrors) });
    }

    HolidayRuleSet parsed = HolidayRuleSetYamlParser.parse(ruleSet.getRuleYaml());
    if (parsed.isEmpty() && ruleSet.getIdExtendsRuleSet() == null) {
      throw new DataViolationException("rule.yaml", "gt.calendar.rule.empty", null);
    }

    Integer id = existingEntity == null ? null : existingEntity.getId();
    if (ruleSet.getIdExtendsRuleSet() != null) {
      if (ruleSet.getIdExtendsRuleSet().equals(id)) {
        throw new DataViolationException("id.extends.rule.set", "gt.calendar.rule.cycle", null);
      }
      requireNoCycle(id, ruleSet.getIdExtendsRuleSet());
    }

    if (ruleSet.getMic() != null) {
      Optional<TradingCalendarRuleSet> other = tradingCalendarRuleSetJpaRepository.findByMic(ruleSet.getMic());
      if (other.isPresent() && !other.get().getId().equals(id)) {
        throw new DataViolationException("mic", "gt.calendar.rule.mic.duplicate",
            new Object[] { ruleSet.getMic(), other.get().getName() });
      }
    }

    Optional<TradingCalendarRuleSet> sameName = tradingCalendarRuleSetJpaRepository.findByName(ruleSet.getName());
    if (sameName.isPresent() && !sameName.get().getId().equals(id)) {
      throw new DataViolationException("name", "gt.calendar.rule.name.duplicate",
          new Object[] { ruleSet.getName() });
    }
  }

  /**
   * Walks the chain of extended rule sets and refuses one that leads back to the set being saved. Without this an
   * inheritance cycle would make every calendar calculation of the involved sets recurse until the stack overflows.
   */
  private void requireNoCycle(Integer idOfSavedSet, Integer idParent) {
    Set<Integer> visited = new HashSet<>();
    Integer current = idParent;
    while (current != null) {
      if (current.equals(idOfSavedSet) || !visited.add(current)) {
        throw new DataViolationException("id.extends.rule.set", "gt.calendar.rule.cycle", null);
      }
      current = tradingCalendarRuleSetJpaRepository.findById(current)
          .map(TradingCalendarRuleSet::getIdExtendsRuleSet).orElse(null);
    }
  }

  @Override
  public void assertDeletable(Integer idTradingCalendarRuleSet) {
    int usedBy = tradingCalendarRuleSetJpaRepository.countUsingStockexchanges(idTradingCalendarRuleSet);
    if (usedBy > 0) {
      throw new DataViolationException("name", "gt.calendar.rule.inuse", new Object[] { usedBy });
    }
    List<TradingCalendarRuleSet> children = tradingCalendarRuleSetJpaRepository.findAll().stream()
        .filter(rs -> idTradingCalendarRuleSet.equals(rs.getIdExtendsRuleSet())).toList();
    if (!children.isEmpty()) {
      throw new DataViolationException("name", "gt.calendar.rule.extended.by",
          new Object[] { children.stream().map(TradingCalendarRuleSet::getName).collect(Collectors.joining(", ")) });
    }
  }

  @Override
  public List<TradingCalendarRuleSet> getAllRuleSets() {
    List<TradingCalendarRuleSet> ruleSets = tradingCalendarRuleSetJpaRepository.findAllByOrderByNameAsc();
    Map<Integer, String> nameById = ruleSets.stream()
        .collect(Collectors.toMap(TradingCalendarRuleSet::getIdTradingCalendarRuleSet, TradingCalendarRuleSet::getName));
    Map<Integer, Integer> usage = tradingCalendarRuleSetJpaRepository.countUsingStockexchangesGrouped().stream()
        .collect(Collectors.toMap(IdRuleSetUsage::getIdTradingCalendarRuleSet, IdRuleSetUsage::getUsedByExchanges));
    ruleSets.forEach(rs -> {
      rs.setNameExtendsRuleSet(rs.getIdExtendsRuleSet() == null ? null : nameById.get(rs.getIdExtendsRuleSet()));
      rs.setUsedByExchanges(usage.getOrDefault(rs.getIdTradingCalendarRuleSet(), 0));
    });
    return ruleSets;
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getRuleSetOptions() {
    return tradingCalendarRuleSetJpaRepository.findAllByOrderByNameAsc().stream()
        .map(rs -> new ValueKeyHtmlSelectOptions(rs.getIdTradingCalendarRuleSet().toString(),
            rs.getMic() == null ? rs.getName() : rs.getName() + " (" + rs.getMic() + ")"))
        .toList();
  }

  @Override
  public Map<String, Integer> getRuleSetIdByMic() {
    return tradingCalendarRuleSetJpaRepository.findAll().stream().filter(rs -> rs.getMic() != null)
        .collect(Collectors.toMap(TradingCalendarRuleSet::getMic,
            TradingCalendarRuleSet::getIdTradingCalendarRuleSet));
  }

  @Override
  public HolidayRuleSet getResolvedRuleSet(Integer idTradingCalendarRuleSet) {
    return resolve(idTradingCalendarRuleSet, null, new HashMap<>());
  }

  /**
   * Resolves a rule set against the chain of sets it extends. The optional {@code overrideYaml} replaces the persisted
   * text of the requested set only, which lets the editor preview unsaved changes without touching the database.
   */
  private HolidayRuleSet resolve(Integer id, String overrideYaml, Map<Integer, HolidayRuleSet> resolved) {
    HolidayRuleSet already = resolved.get(id);
    if (already != null) {
      return already;
    }
    TradingCalendarRuleSet entity = tradingCalendarRuleSetJpaRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Trading calendar rule set not found: " + id));
    HolidayRuleSet own = HolidayRuleSetYamlParser
        .parse(overrideYaml != null ? overrideYaml : entity.getRuleYaml());
    // A cycle cannot normally exist because saving rejects it, but a marker guards against a chain corrupted
    // outside the application, which would otherwise recurse until the stack overflows.
    resolved.put(id, own);
    HolidayRuleSet effective = entity.getIdExtendsRuleSet() == null ? own
        : HolidayRuleSetResolver.merge(resolve(entity.getIdExtendsRuleSet(), null, resolved), own);
    resolved.put(id, effective);
    return effective;
  }

  @Override
  public SortedMap<String, String> previewClosures(Integer idTradingCalendarRuleSet, String ruleYaml, int year) {
    HolidayRuleSet ruleSet = resolve(idTradingCalendarRuleSet, ruleYaml, new HashMap<>());
    SortedMap<String, String> closures = new TreeMap<>();
    for (Map.Entry<LocalDate, String> entry : ruleSet.closuresWithRuleName(year).entrySet()) {
      closures.put(entry.getKey().toString(), entry.getValue());
    }
    return closures;
  }

  @Override
  public List<String> validateRuleYaml(String ruleYaml) {
    return tradingCalendarRuleYamlValidator.validate(ruleYaml);
  }
}

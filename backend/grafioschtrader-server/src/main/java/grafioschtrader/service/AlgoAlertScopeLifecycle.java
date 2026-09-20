package grafioschtrader.service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import grafioschtrader.repository.AlgoAlertEvaluationStateJpaRepository;

/** Resets affected pairs on hierarchy edits, even when disable and re-enable occur between scheduler scans. */
@Service
public class AlgoAlertScopeLifecycle {
  private final AlgoAlertScopeResolver resolver;
  private final AlgoAlertEvaluationStateJpaRepository repository;
  private final AlgoAlertStateService crossings;

  public AlgoAlertScopeLifecycle(@Lazy AlgoAlertScopeResolver resolver,
      AlgoAlertEvaluationStateJpaRepository repository, AlgoAlertStateService crossings) {
    this.resolver = resolver;
    this.repository = repository;
    this.crossings = crossings;
  }

  /** Captures effective activation before saving a hierarchy node. */
  public Map<String, AlgoAlertScope> snapshot(Integer tenant) {
    return resolver.resolveForTenant(tenant).stream()
        .collect(Collectors.toMap(this::key, Function.identity(), (first, _) -> first));
  }

  /** Removes scheduling and crossing state only for pairs whose effective scope changed. */
  public void changed(Integer tenant, Map<String, AlgoAlertScope> before) {
    Map<String, AlgoAlertScope> after = snapshot(tenant);
    before.forEach((key, old) -> {
      AlgoAlertScope current = after.get(key);
      if (current == null || old.active() != current.active()) {
        crossings.discard(old.strategy().getIdAlgoRuleStrategy(), old.security().getIdSecuritycurrency());
        repository.findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrency(tenant, old.strategy().getIdAlgoRuleStrategy(),
            old.security().getIdSecuritycurrency()).ifPresent(repository::delete);
      }
    });
  }

  private String key(AlgoAlertScope scope) {
    return scope.strategy().getIdAlgoRuleStrategy() + ":" + scope.security().getIdSecuritycurrency();
  }
}

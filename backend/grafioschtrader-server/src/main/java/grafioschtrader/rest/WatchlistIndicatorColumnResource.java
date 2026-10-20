package grafioschtrader.rest;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.entities.WatchlistIndicatorColumn;
import grafioschtrader.evalex.*;
import grafioschtrader.repository.WatchlistIndicatorColumnJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Tenant-scoped editor endpoints; validation and atomic bulk writes live in the repository. */
@RestController
@RequestMapping("/api/watchlistindicatorcolumn")
@Tag(name = "Watchlist indicator columns")
public class WatchlistIndicatorColumnResource extends UpdateCreateDeleteWithTenantResource<WatchlistIndicatorColumn> {
  @Autowired
  private WatchlistIndicatorColumnJpaRepository repository;
  @Autowired
  private grafioschtrader.repository.SecurityJpaRepository securities;
  @Autowired
  private grafioschtrader.repository.CurrencypairJpaRepository currencies;

  @GetMapping("/benchmarks")
  public Map<Integer, String> benchmarks() {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    var names = new HashMap<Integer, String>();
    columns().stream().map(WatchlistIndicatorColumn::getIdBenchmark).filter(Objects::nonNull).distinct().forEach(id -> {
      var security = securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(id,
          user.getIdTenant());
      if (security != null)
        names.put(id, security.getName());
      else
        currencies.findById(id).ifPresent(pair -> names.put(id, pair.getName()));
    });
    return names;
  }

  public WatchlistIndicatorColumnResource() {
    super(WatchlistIndicatorColumn.class);
  }

  @Override
  protected WatchlistIndicatorColumnJpaRepository getUpdateCreateJpaRepository() {
    return repository;
  }

  @GetMapping("/markcolors")
  public List<Map<String, String>> markColors() {
    return Arrays.stream(grafioschtrader.types.IndicatorMarkColors.values())
        .map(c -> Map.of("key", c.name(), "value", c.name())).toList();
  }

  @GetMapping
  public List<WatchlistIndicatorColumn> columns() {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return repository.findByIdTenantOrderByUiOrder(user.getIdTenant());
  }

  @PutMapping("/order")
  public List<WatchlistIndicatorColumn> order(@RequestBody List<Integer> ids) {
    return repository.reorder(ids);
  }

  @PostMapping("/defaults")
  public List<WatchlistIndicatorColumn> defaults() {
    return repository.createDefaults();
  }

  @PostMapping("/resulttype")
  public Map<String, Object> resultType(@RequestBody String expression) {
    try {
      AlertExpressionSupport.validate(expression, EnumSet.allOf(IndicatorData.class));
    } catch (AlertExpressionException e) {
      throw new grafiosch.exceptions.DataViolationException("expression", e.getKey(), e.getArguments());
    }
    return Map.of("resultType", AlertExpressionSupport.resultType(expression));
  }
}

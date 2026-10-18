package grafioschtrader.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import grafiosch.BaseConstants;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.dto.AIKeyTestResult;
import grafioschtrader.dto.TenantAIProviderOptions;
import grafioschtrader.entities.TenantAIProvider;
import grafioschtrader.repository.AIRequestLogJpaRepository;
import grafioschtrader.repository.TenantAIProviderJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.udffill.ai.AIChatModelProvider;

/** Settings of the caller's home tenant. Repository validation also protects non-REST write paths. */
@RestController
@RequestMapping("/api/tenantaiprovider")
public class TenantAIProviderResource extends UpdateCreateDeleteWithTenantResource<TenantAIProvider> {
  private final TenantAIProviderJpaRepository repository;
  private final GlobalparametersService parameters;
  private final AIChatModelProvider models;
  private final AIRequestLogJpaRepository aiRequestLogJpaRepository;

  public TenantAIProviderResource(TenantAIProviderJpaRepository repository, GlobalparametersService parameters,
      AIChatModelProvider models, AIRequestLogJpaRepository aiRequestLogJpaRepository) {
    super(TenantAIProvider.class);
    this.repository = repository;
    this.parameters = parameters;
    this.models = models;
    this.aiRequestLogJpaRepository = aiRequestLogJpaRepository;
  }

  /** The keys of the home tenant, each with its requests of the current UTC day. */
  @GetMapping
  public List<TenantAIProvider> getAll() {
    List<TenantAIProvider> rows = repository
        .findByIdTenantOrderByPriorityAscIdTenantAIProviderAsc(currentUser().getIdTenant());
    LocalDateTime startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
    rows.forEach(r -> r.setRequestsToday(
        aiRequestLogJpaRepository.countByIdTenantAIProviderAndRequestTimeGreaterThanEqual(r.getId(), startOfDay)));
    return rows;
  }

  @GetMapping("/providers")
  public TenantAIProviderOptions getProviders() {
    List<String> allowed = parameters.getAITenantProviders();
    return new TenantAIProviderOptions(
        allowed.stream().map(p -> new ValueKeyHtmlSelectOptions(p, AIChatModelProvider.PROVIDER_NAMES.get(p))).toList(),
        allowed.stream().filter(AIChatModelProvider.KEY_PROVIDERS::contains).collect(Collectors.toSet()),
        allowed.stream().collect(Collectors.toMap(p -> p, AIChatModelProvider::defaultModel)),
        AIChatModelProvider.PROVIDER_NAMES);
  }

  @PostMapping("/{id}/test")
  public AIKeyTestResult testKey(@PathVariable Integer id) {
    return models.testKey(id, currentUser());
  }

  @Override
  protected ResponseEntity<TenantAIProvider> createEntity(TenantAIProvider entity) throws Exception {
    checkWrite();
    return super.createEntity(entity);
  }

  @Override
  protected ResponseEntity<TenantAIProvider> updateEntity(TenantAIProvider entity) throws Exception {
    if (entity.getId() != null) {
      checkWrite();
    }
    return super.updateEntity(entity);
  }

  @Override
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteResource(@PathVariable Integer id) {
    checkWrite();
    return super.deleteResource(id);
  }

  private void checkWrite() {
    User user = currentUser();
    if (user.getIdTenant() == null || !user.getIdTenant().equals(user.getActualIdTenant())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    dailyLimitService.check(user, TenantAIProvider.class.getSimpleName(), 1);
    // The inherited create/update/delete paths log exactly once through DailyLimitService.log.
  }

  private static User currentUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }

  @Override
  protected TenantAIProviderJpaRepository getUpdateCreateJpaRepository() {
    return repository;
  }
}

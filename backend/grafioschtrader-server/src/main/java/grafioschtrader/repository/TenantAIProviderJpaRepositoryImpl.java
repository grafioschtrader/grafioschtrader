package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.entities.TenantAIProvider;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.udffill.ai.AIChatModelProvider;

/** Validates ownership and credentials before any settings write; invalidates clients only after commit. */
public class TenantAIProviderJpaRepositoryImpl extends BaseRepositoryImpl<TenantAIProvider>
    implements TenantAIProviderJpaRepositoryCustom {
  private final TenantAIProviderJpaRepository repository;
  private final TenantJpaRepository tenants;
  private final GlobalparametersService parameters;
  private final AIChatModelProvider models;

  public TenantAIProviderJpaRepositoryImpl(@Lazy TenantAIProviderJpaRepository repository, TenantJpaRepository tenants,
      GlobalparametersService parameters, @Lazy AIChatModelProvider models) {
    this.repository = repository;
    this.tenants = tenants;
    this.parameters = parameters;
    this.models = models;
  }

  @Override
  @Transactional
  public TenantAIProvider saveOnlyAttributes(TenantAIProvider input, TenantAIProvider ignored,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    User user = requireHomeTenant();
    if (input.getIdTenant() != null && !user.getIdTenant().equals(input.getIdTenant())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    TenantAIProvider stored = input.getId() == null ? new TenantAIProvider() : ownRow(input.getId(), user);
    String oldProvider = stored.getProvider();
    validate(input, stored, user);
    String key = input.getApiKey();
    stored.setIdTenant(user.getIdTenant());
    stored.setProvider(input.getProvider());
    stored.setModel(input.getModel() == null || input.getModel().isBlank() ? null : input.getModel().strip());
    stored.setPriority(input.getPriority());
    stored.setActive(input.isActive());
    stored.setDailyRequestLimit(input.getDailyRequestLimit());
    if (!AIChatModelProvider.KEY_PROVIDERS.contains(input.getProvider())) {
      stored.storeApiKey(null);
    } else if (key != null && !key.isBlank()) {
      stored.storeApiKey(key.strip());
    }
    TenantAIProvider saved = repository.saveAndFlush(stored);
    invalidate(user.getIdTenant(), oldProvider, saved.getProvider());
    return saved;
  }

  private void validate(TenantAIProvider input, TenantAIProvider stored, User user) {
    if (!parameters.getAITenantProviders().contains(input.getProvider())) {
      throw new DataViolationException("provider", "gt.ai.tenant.provider.invalid", null);
    }
    repository.findByIdTenantAndProvider(user.getIdTenant(), input.getProvider()).ifPresent(other -> {
      if (!Objects.equals(other.getId(), input.getId())) {
        throw new DataViolationException("provider", "gt.ai.tenant.provider.duplicate", null);
      }
    });
    String key = input.getApiKey();
    if (key != null && key.length() > 351) {
      throw new DataViolationException("api.key", "gt.ai.tenant.key.invalid", null);
    }
    if (AIChatModelProvider.KEY_PROVIDERS.contains(input.getProvider()) && (key == null || key.isBlank())
        && stored.decryptApiKey() == null) {
      throw new DataViolationException("api.key", "gt.ai.tenant.key.required", null);
    }
    if (input.getModel() != null && !input.getModel().isBlank()
        && (!input.getModel().matches("[A-Za-z0-9._:/-]+") || input.getModel().length() > 64)) {
      throw new DataViolationException("model", "gt.ai.tenant.model.invalid", null);
    }
    if (input.getPriority() < 1) {
      throw new DataViolationException("priority", "gt.ai.tenant.priority.invalid", null);
    }
    if (input.getDailyRequestLimit() != null
        && (input.getDailyRequestLimit() < 1 || input.getDailyRequestLimit() > 100000)) {
      throw new DataViolationException("DAILY_REQUEST_LIMIT", "gt.ai.tenant.limit.invalid", null);
    }
  }

  @Override
  @Transactional
  public int delEntityWithTenant(Integer id, Integer idTenant) {
    User user = requireHomeTenant();
    if (!user.getIdTenant().equals(idTenant)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    TenantAIProvider row = ownRow(id, user);
    repository.delete(row);
    repository.flush();
    invalidate(idTenant, row.getProvider(), row.getProvider());
    return 1;
  }

  private TenantAIProvider ownRow(Integer id, User user) {
    return repository.findByIdTenantAIProviderAndIdTenant(id, user.getIdTenant())
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
  }

  private User requireHomeTenant() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (user.getIdTenant() == null || !user.getIdTenant().equals(user.getActualIdTenant())
        || tenants.findById(user.getIdTenant()).filter(t -> t.getTenantKindType() == TenantKindType.MAIN).isEmpty()) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return user;
  }

  private void invalidate(Integer idTenant, String previous, String provider) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        models.resetTenant(idTenant, previous);
        models.resetTenant(idTenant, provider);
      }
    });
  }
}

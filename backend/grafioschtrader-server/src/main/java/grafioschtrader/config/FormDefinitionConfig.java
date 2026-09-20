package grafioschtrader.config;

import org.springframework.context.annotation.Configuration;

import grafiosch.dynamic.model.FormDefinitionRegistry;
import grafioschtrader.algo.SimulationRunRequestDTO;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.entities.BankruptSecurity;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.SecurityBondTerms;
import jakarta.annotation.PostConstruct;

/**
 * Registers the entities whose dynamic form definition may be served by the generic
 * {@code /globalparameters/formdefinition/{entityName}} endpoint. Only entities that annotate their input fields with
 * {@code @DynamicFormField} belong here. Keeping the registration in the application layer keeps the reusable
 * {@code grafiosch-base} library free of grafioschtrader references.
 */
@Configuration
public class FormDefinitionConfig {

  @PostConstruct
  public void registerFormDefinitionEntities() {
    FormDefinitionRegistry.register(BankruptSecurity.class);
    FormDefinitionRegistry.register(Cashaccount.class);
    FormDefinitionRegistry.register(SimulationTenantCreateDTO.class);
    FormDefinitionRegistry.register(SimulationRunRequestDTO.class);
    FormDefinitionRegistry.register(SecurityBondTerms.class);
    FormDefinitionRegistry.register(grafioschtrader.entities.Securityaccount.class);
    FormDefinitionRegistry.register(grafioschtrader.entities.TradingPlatformPlan.class);
    FormDefinitionRegistry.register(grafioschtrader.entities.AlgoAssetclass.class);
  }
}

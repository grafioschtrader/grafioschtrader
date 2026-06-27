package grafioschtrader.config;

import org.springframework.context.annotation.Configuration;

import grafiosch.dynamic.model.FormDefinitionRegistry;
import grafioschtrader.entities.Cashaccount;
import jakarta.annotation.PostConstruct;

/**
 * Registers the entities whose dynamic form definition may be served by the generic
 * {@code /globalparameters/formdefinition/{entityName}} endpoint. Only entities that annotate their
 * input fields with {@code @DynamicFormField} belong here. Keeping the registration in the
 * application layer keeps the reusable {@code grafiosch-base} library free of grafioschtrader
 * references.
 */
@Configuration
public class FormDefinitionConfig {

  @PostConstruct
  public void registerFormDefinitionEntities() {
    FormDefinitionRegistry.register(Cashaccount.class);
  }
}

package grafiosch.integration.security;

import java.util.Collections;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.dto.ConfigurationWithLogin;
import grafiosch.security.TokenAuthentication;

/** Minimal login configuration supplied by an application built only on the reusable backend modules. */
@Service
public class IntegrationTokenAuthenticationService extends TokenAuthentication {

  @Override
  @Transactional(readOnly = true)
  public ConfigurationWithLogin getConfigurationWithLogin(boolean uiShowMyProperty, String mostPrivilegedRole,
      boolean passwordRegexOk, Integer idTenant) {
    ConfigurationWithLogin configuration = new ConfigurationWithLogin(getAllEntitiyNamesWithTheirKeys(),
        getGlobalConstantsFieldsByFieldPrefix(BaseConstants.class, "FIELD_SIZE"), uiShowMyProperty, mostPrivilegedRole,
        passwordRegexOk, getGlobalConstantsFieldsByFieldPrefix(BaseConstants.class, "FID"));
    configuration.useFeatures = Collections.emptySet();
    return configuration;
  }
}

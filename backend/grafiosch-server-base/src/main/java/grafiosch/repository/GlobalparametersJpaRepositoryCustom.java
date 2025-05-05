package grafiosch.repository;

import java.util.List;

import grafiosch.dto.PasswordRegexProperties;
import grafiosch.dto.TenantLimit;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.Globalparameters;
import jakarta.persistence.EntityManager;

public interface GlobalparametersJpaRepositoryCustom {

  EntityManager getEntityManager();
 
  /**
   * How many days of background task history are kept
   */
  int getTaskDataDaysPreserve();

  /**
   * There is a maximum number of data records for certain entities, which can be queried.
   */
  int getMaxValueByKey(String key);

  List<TenantLimit> getMaxTenantLimitsByMsgKeys(List<String> msgKeys);

  PasswordRegexProperties getPasswordRegexProperties() throws Exception;

 

  int getJWTExpirationMinutes();

  List<ValueKeyHtmlSelectOptions> getCountriesForSelectBox();

  String getLanguageProperties(final String language);

  int getAlertBitmap();

  /**
   * Maximum number of violations against request limit before blocking the user
   */
  int getMaxLimitExceededCount();

  int getMaxSecurityBreachCount();

  List<ValueKeyHtmlSelectOptions> getAllZoneIds();

  List<ValueKeyHtmlSelectOptions> getSupportedLocales();

  Globalparameters saveOnlyAttributes(Globalparameters globalparameters) throws Exception;

}

package grafioschtrader.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.entities.User;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Answers whether an instrument id may be seen by a user. A currency pair is always public, whereas a security may be
 * private to another tenant, so the two are resolved separately.
 *
 * <p>
 * It exists because more than one write path takes an {@code idSecuritycurrency} straight from the request body and has
 * to reject an id the caller must not see. Without the check such an endpoint accepts any id the foreign key tolerates,
 * which turns a table keyed by {@code (tenant, instrument)} into free storage rather than a bounded configuration.
 * </p>
 *
 * <p>
 * The method returns a boolean rather than throwing, so each caller keeps the field name and message key of its own
 * request contract.
 * </p>
 */
@Service
public class InstrumentVisibilityService {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  /**
   * Reports whether the instrument exists and is visible to the user.
   *
   * @param idSecuritycurrency id of the security or currency pair, may be null
   * @param user               the acting user, whose tenant decides access to a tenant-private security
   * @return true when the id belongs to a currency pair, or to a security that is public or private to the user's own
   *         tenant; false when the id is null, unknown, or private to another tenant
   */
  public boolean isVisible(Integer idSecuritycurrency, User user) {
    if (idSecuritycurrency == null) {
      return false;
    }
    return currencypairJpaRepository.existsById(idSecuritycurrency)
        || securityJpaRepository.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency,
            user.getIdTenant()) != null;
  }
}

package grafioschtrader.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.service.IMailUserToUserContextVerifier;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.WatchlistJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Context verifier for an initial user-to-user message started from a security or currency pair (e.g. the watchlist
 * "mail to creator" action). A non-privileged user may contact the creator of a security only when both hold:
 * the recipient really is the security's creator, and the security is on a watchlist owned by the sender's tenant.
 * The latter blunts forgery: a sender cannot message the creator of an arbitrary security they never added.
 */
@Component
public class SecuritycurrencyMailContextVerifier implements IMailUserToUserContextVerifier {

  /** Context entity-type key carried by the message; matches {@link Securitycurrency}'s simple name. */
  public static final String CONTEXT_ENTITY = Securitycurrency.class.getSimpleName();

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private WatchlistJpaRepository watchlistJpaRepository;

  @Override
  public boolean supports(String contextEntity) {
    return CONTEXT_ENTITY.equals(contextEntity);
  }

  @Override
  public void verify(User fromUser, Integer idUserTo, Integer idEntityContext) {
    Securitycurrency<?> securitycurrency = entityManager.find(Securitycurrency.class, idEntityContext);
    if (securitycurrency == null || !idUserTo.equals(securitycurrency.getCreatedBy())
        || watchlistJpaRepository
            .getAllWatchlistsWithSecurityByIdSecuritycurrency(fromUser.getIdTenant(), idEntityContext).isEmpty()) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }
}

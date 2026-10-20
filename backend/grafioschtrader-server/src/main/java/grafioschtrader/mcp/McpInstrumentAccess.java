package grafioschtrader.mcp;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.repository.CashaccountJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;

/**
 * Checks every id a client passes before a report sees it. The reports silently skip foreign ids - an empty answer for
 * a foreign cash account, a foreign account dropped from a selection - and an empty report must never be the answer to
 * a foreign id. A model guesses ids, so a foreign id is answered with {@link McpErrorCode#NOT_OWNED} and does not count
 * as a security breach of the user.
 */
@Component
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class McpInstrumentAccess {

  private final SecurityJpaRepository securityJpaRepository;
  private final PortfolioJpaRepository portfolioJpaRepository;
  private final SecurityaccountJpaRepository securityaccountJpaRepository;
  private final CashaccountJpaRepository cashaccountJpaRepository;

  public McpInstrumentAccess(SecurityJpaRepository securityJpaRepository,
      PortfolioJpaRepository portfolioJpaRepository, SecurityaccountJpaRepository securityaccountJpaRepository,
      CashaccountJpaRepository cashaccountJpaRepository) {
    this.securityJpaRepository = securityJpaRepository;
    this.portfolioJpaRepository = portfolioJpaRepository;
    this.securityaccountJpaRepository = securityaccountJpaRepository;
    this.cashaccountJpaRepository = cashaccountJpaRepository;
  }

  /**
   * Returns a security the tenant may see: a public one, with or without holdings, or a private one of the tenant. The
   * reports load an instrument without any tenant check, so this runs before the first use of a passed id. An invisible
   * security is refused without revealing its name, ISIN or anything else about it.
   *
   * @param caller             the caller, whose home tenant decides the visibility
   * @param idSecuritycurrency the id passed by the client
   * @param parameter          the name of the tool parameter, for the error
   * @return the visible security
   */
  public Security requireVisibleSecurity(McpCaller caller, Integer idSecuritycurrency, String parameter) {
    Security security = idSecuritycurrency == null ? null
        : securityJpaRepository.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency,
            caller.idTenant());
    if (security == null) {
      throw McpToolException.notOwned(parameter);
    }
    return security;
  }

  public Portfolio requireOwnPortfolio(McpCaller caller, Integer idPortfolio, String parameter) {
    Portfolio portfolio = idPortfolio == null ? null
        : portfolioJpaRepository.findByIdTenantAndIdPortfolio(caller.idTenant(), idPortfolio);
    if (portfolio == null) {
      throw McpToolException.notOwned(parameter);
    }
    return portfolio;
  }

  public Cashaccount requireOwnCashaccount(McpCaller caller, Integer idCashaccount, String parameter) {
    Cashaccount cashaccount = idCashaccount == null ? null
        : cashaccountJpaRepository.findByIdSecuritycashAccountAndIdTenant(idCashaccount, caller.idTenant());
    if (cashaccount == null) {
      throw McpToolException.notOwned(parameter);
    }
    return cashaccount;
  }

  public void requireOwnCashaccounts(McpCaller caller, List<Integer> idsCashaccount, String parameter) {
    idsCashaccount.forEach(id -> requireOwnCashaccount(caller, id, parameter));
  }

  public void requireOwnSecurityaccounts(McpCaller caller, List<Integer> idsSecurityaccount, String parameter) {
    for (Integer id : idsSecurityaccount) {
      Securityaccount securityaccount = id == null ? null
          : securityaccountJpaRepository.findByIdSecuritycashAccountAndIdTenant(id, caller.idTenant());
      if (securityaccount == null) {
        throw McpToolException.notOwned(parameter);
      }
    }
  }
}

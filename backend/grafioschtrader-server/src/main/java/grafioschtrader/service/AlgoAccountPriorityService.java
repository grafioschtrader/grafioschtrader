package grafioschtrader.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.common.SecurityaccountTradingEligibility;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoAssetclassSecurity;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;

/**
 * The security account priorities of an asset class or an instrument in an algo hierarchy
 * ({@link AlgoAssetclassSecurity#getIdSecurityaccount1()} / {@link AlgoAssetclassSecurity#getIdSecurityaccount2()}).
 *
 * <p>
 * A priority states where the user has found an instrument cheapest to trade, for example direct bonds only at the
 * broker with the lowest bond commission. A simulation buys at that account and moves the missing cash there from the
 * other brokers. The priority is therefore only meaningful for an account whose trading periods allow the instrument
 * type at all, which is what the options offered in the dialog and the check on save both enforce.
 * </p>
 *
 * <p>
 * Neither asks for a date. A simulation replays past years, so a period that has already ended still counts; whether it
 * covers the day of a replayed fill is decided by the replay itself.
 * </p>
 */
@Service
public class AlgoAccountPriorityService {

  private final SecurityaccountJpaRepository securityaccounts;
  private final SecurityJpaRepository securities;
  private final AssetclassJpaRepository assetclasses;

  public AlgoAccountPriorityService(SecurityaccountJpaRepository securityaccounts, SecurityJpaRepository securities,
      AssetclassJpaRepository assetclasses) {
    this.securityaccounts = securityaccounts;
    this.securities = securities;
    this.assetclasses = assetclasses;
  }

  /**
   * The security accounts of a tenant that may be named as priority for an instrument or an asset class, labelled
   * {@code portfolio / account} and ordered that way.
   *
   * @param idTenant           the tenant whose accounts are offered
   * @param idSecuritycurrency the instrument of an {@link AlgoSecurity}, or null
   * @param idAssetClass       the asset class of an {@link AlgoAssetclass}, or null; with neither, as for a custom
   *                           category, every account is offered
   * @return the options, keyed by the id of the security account
   */
  public List<ValueKeyHtmlSelectOptions> getOptions(Integer idTenant, Integer idSecuritycurrency,
      Integer idAssetClass) {
    Assetclass assetclass = idSecuritycurrency != null ? securityOf(idSecuritycurrency, idTenant).getAssetClass()
        : idAssetClass != null ? assetclasses.findById(idAssetClass).orElse(null) : null;
    return securityaccounts.findByIdTenant(idTenant).stream()
        .filter(sa -> SecurityaccountTradingEligibility.allowsEver(sa, assetclass))
        .sorted(Comparator.comparing((Securityaccount sa) -> sa.getPortfolio().getName())
            .thenComparing(Securityaccount::getName))
        .map(sa -> new ValueKeyHtmlSelectOptions(String.valueOf(sa.getIdSecuritycashAccount()),
            sa.getPortfolio().getName() + " / " + sa.getName()))
        .toList();
  }

  /**
   * Checks the priorities of an asset class or an instrument node before it is saved: both accounts belong to the
   * tenant of the node, the second is only given together with a different first one, and, where the instrument type is
   * known, the trading periods of each account allow it.
   *
   * @param node       the node being saved, its security or asset class already resolved or at least carrying its id
   * @param assetclass the asset class the type is read from, or null for a custom category
   * @throws DataViolationException when a rule is broken
   */
  public void validate(AlgoAssetclassSecurity node, Assetclass assetclass) {
    Integer id1 = node.getIdSecurityaccount1();
    Integer id2 = node.getIdSecurityaccount2();
    if (id1 == null && id2 == null) {
      return;
    }
    if (id1 == null) {
      throw new DataViolationException("algo.securityaccount.1", "gt.algo.securityaccount.second.without.first", null);
    }
    if (id1.equals(id2)) {
      throw new DataViolationException("algo.securityaccount.2", "gt.algo.securityaccount.same", null);
    }
    Map<Integer, Securityaccount> own = securityaccounts.findByIdTenant(node.getIdTenant()).stream()
        .collect(Collectors.toMap(Securityaccount::getIdSecuritycashAccount, Function.identity()));
    Assetclass resolved = assetclass == null || assetclass.getIdAssetClass() == null ? null
        : assetclasses.findById(assetclass.getIdAssetClass()).orElse(null);
    checkAccount(own, id1, resolved, "algo.securityaccount.1");
    if (id2 != null) {
      checkAccount(own, id2, resolved, "algo.securityaccount.2");
    }
  }

  /**
   * The asset class that decides the instrument type of an instrument node.
   *
   * @param algoSecurity the node, carrying at least the id of its security
   * @return the asset class, or null when the node carries no security
   */
  public Assetclass assetclassOf(AlgoSecurity algoSecurity) {
    return algoSecurity.getSecurity() == null ? null
        : securityOf(algoSecurity.getSecurity().getIdSecuritycurrency(), algoSecurity.getIdTenant()).getAssetClass();
  }

  private void checkAccount(Map<Integer, Securityaccount> own, Integer id, Assetclass assetclass, String field) {
    Securityaccount account = own.get(id);
    if (account == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    if (!SecurityaccountTradingEligibility.allowsEver(account, assetclass)) {
      throw new DataViolationException(field, "gt.algo.securityaccount.type.not.allowed", new Object[] {
          account.getName(), assetclass.getCategoryType(), assetclass.getSpecialInvestmentInstrument() });
    }
  }

  private Security securityOf(Integer idSecuritycurrency, Integer idTenant) {
    Security security = securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency,
        idTenant);
    if (security == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return security;
  }
}

package grafioschtrader.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.dto.SecurityCurrencypairDerivedLinks;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;

public class SecurityDerivedLinkJpaRepositoryImpl implements SecurityDerivedLinkJpaRepositoryCustom {

  @Autowired
  private SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Override
  public SecurityCurrencypairDerivedLinks getDerivedInstrumentsLinksForSecurity(Integer idSecurity, Integer idTenant) {
    return getDerivedInstrumentsLinksForSecurity(
        securityJpaRepository.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecurity, idTenant));
  }

  @Override
  public SecurityCurrencypairDerivedLinks getDerivedInstrumentsLinksForSecurity(Security security) {
    if (security != null) {
      List<SecurityDerivedLink> securityDerivedLinks = securityDerivedLinkJpaRepository
          .findByIdEmIdSecuritycurrencyOrderByIdEmIdSecuritycurrency(security.getIdSecuritycurrency());
      List<Integer> securityDerivedLinksIds = securityDerivedLinks.stream()
          .map(SecurityDerivedLink::getIdLinkSecuritycurrency).collect(Collectors.toList());

      securityDerivedLinksIds.add(security.getIdLinkSecuritycurrency());
      return new SecurityCurrencypairDerivedLinks(securityDerivedLinks,
          securityJpaRepository.findAllById(securityDerivedLinksIds),
          currencypairJpaRepository.findAllById(securityDerivedLinksIds));
    }
    return new SecurityCurrencypairDerivedLinks();
  }

}

package grafioschtrader.repository;

import grafioschtrader.dto.SecurityCurrencypairDerivedLinks;
import grafioschtrader.entities.Security;

public interface SecurityDerivedLinkJpaRepositoryCustom {

  SecurityCurrencypairDerivedLinks getDerivedInstrumentsLinksForSecurity(Integer idSecurity, Integer idTenant);

  SecurityCurrencypairDerivedLinks getDerivedInstrumentsLinksForSecurity(Security security);

}

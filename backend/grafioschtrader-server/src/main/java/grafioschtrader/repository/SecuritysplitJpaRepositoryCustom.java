package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

import grafioschtrader.dto.SecuritysplitDeleteAndCreateMultiple;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;

public interface SecuritysplitJpaRepositoryCustom {

  Map<Integer, List<Securitysplit>> getSecuritysplitMapByIdSecuritycashaccount(Integer idSecuritycashaccount);

  Map<Integer, List<Securitysplit>> getSecuritysplitMapByIdWatchlist(Integer idWatchlist);

  Map<Integer, List<Securitysplit>> getSecuritysplitMapByIdSecuritycurrency(Integer idSecuritycurrency);

  Map<Integer, List<Securitysplit>> getSecuritysplitMapByIdTenant(Integer idTenant);

  List<Securitysplit> deleteAndCreateMultiple(
      SecuritysplitDeleteAndCreateMultiple securitysplitDeleteAndCreateMultiple);

  List<String> loadAllSplitDataFromConnector(Security security, Date requestedSplitdate);
}

package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

  /**
   * If the split comes from a split calendar, it may take a few days until the
   * split is also mapped to the corresponding security at the data provider.
   * Thus, the data provider must be queried periodically for the requested split.
   * It is possible that the split from the calendar was internally assigned to a
   * wrong security, therefore the repetitive query is terminated after a certain
   * time.
   *
   *
   * @param security
   * @param requestedSplitdate
   * @return
   */
  List<String> loadAllSplitDataFromConnectorForSecurity(Security security, Date requestedSplitdate);

  /**
   * Loads the historical price data of a security if it reflects the split,
   * otherwise another task is created for the future which repeats this process.
   *
   * @param security
   * @param securitysplits
   * @param youngestSplitDate
   * @param requireHoldingBuild
   * @throws Exception
   */
  public void historicalDataUpdateWhenAdjusted(Security security, List<Securitysplit> securitysplits,
      Optional<Date> youngestSplitDate, boolean requireHoldingBuild) throws Exception;
}

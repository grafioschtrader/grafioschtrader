package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.Securitysplit;

public interface SecuritysplitJpaRepository
    extends JpaRepository<Securitysplit, Integer>, SecuritysplitJpaRepositoryCustom {

  Long deleteByIdSecuritycurrency(Integer idSecuritycurrency);

  Long deleteByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);

  List<Securitysplit> findByIdSecuritycurrencyOrderBySplitDateAsc(Integer idSecuritycurrency);

  List<Securitysplit> findByIdSecuritycurrencyInOrderByIdSecuritycurrencyAscSplitDateAsc(Set<Integer> idSecurity);

  @Query(nativeQuery = true)
  List<Securitysplit> getByIdWatchlist(Integer idWatchlist);

  @Query(nativeQuery = true)
  List<Securitysplit> getByIdTenant(Integer idTenant);

  @Query(nativeQuery = true)
  Double getSplitFactorAfterThanEqualDate(Integer idSecuritycurrency, Date date);

  @Query(nativeQuery = true)
  List<Securitysplit> getByIdSecuritycashaccount(Integer idSecuritycashaccount);
}

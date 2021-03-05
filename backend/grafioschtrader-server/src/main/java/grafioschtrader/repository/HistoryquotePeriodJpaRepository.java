package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.entities.HistoryquotePeriod;

@Repository
public interface HistoryquotePeriodJpaRepository
    extends JpaRepository<HistoryquotePeriod, Integer>, HistoryquotePeriodJpaRepositoryCustom {

  List<HistoryquotePeriod> findByIdSecuritycurrencyOrderByFromDate(Integer idSecuritycurrency);

  List<HistoryquotePeriod> findByIdSecuritycurrencyAndCreateTypeOrderByFromDate(Integer idSecuritycurrency,
      byte createType);

  long deleteByIdSecuritycurrency(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<IDateAndClose> getDateAndCloseByIdSecurity(Integer idSecurity);

  @Query(nativeQuery = true)
  void updatLastPrice();
}

package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.entities.HistoryquotePeriod;

public interface HistoryquotePeriodJpaRepository
    extends JpaRepository<HistoryquotePeriod, Integer>, HistoryquotePeriodJpaRepositoryCustom {

  List<HistoryquotePeriod> findByIdSecuritycurrencyOrderByFromDate(Integer idSecuritycurrency);

  List<HistoryquotePeriod> findByIdSecuritycurrencyAndCreateTypeOrderByFromDate(Integer idSecuritycurrency,
      byte createType);

  long deleteByIdSecuritycurrency(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<IDateAndClose> getDateAndCloseByIdSecurity(Integer idSecurity);

  /**
   * Update the last price of the actual period into the last price of the
   * security. Should be called every day, because within a day the price can not
   * change.
   */
  @Query(nativeQuery = true)
  void updatLastPrice();
}

package grafioschtrader.repository;

import java.util.List;

import grafiosch.entities.User;
import grafioschtrader.dto.HistoryquotePeriodDeleteAndCreateMultiple;
import grafioschtrader.entities.HistoryquotePeriod;
import grafioschtrader.entities.Security;

public interface HistoryquotePeriodJpaRepositoryCustom {

  /**
   * When the lifetime of a security with history quote period is changed the corresponding period must also be
   * adjusted.
   */
  void adjustHistoryquotePeriod(Security security);

  /**
   * History quote period are created manually, normally used when one price does not fit the whole lifetime of a
   * security
   */
  List<HistoryquotePeriod> deleteAndCreateMultiple(User user, HistoryquotePeriodDeleteAndCreateMultiple hpdacm);
}

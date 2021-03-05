package grafioschtrader.task.exec;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.DividendSplit;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.CreateType;

/**
 * 
 *
 * @param <S> Security splits or dividends.
 */
public abstract class UpdateDividendSplitForSecurity<S extends DividendSplit> {

  @Autowired(required = false)
  protected List<IFeedConnector> feedConnectors = new ArrayList<>();

  @Autowired
  protected SecurityJpaRepository securityJpaRepository;
  
  protected List<S> updateDividendSplitData(Security security, List<S> dividendSplitsRead, List<S> existingDividendsSplits,
      JpaRepository<S, Integer> jpaRepository) {
    
    List<S> canCreateSplits = dividendSplitsRead
        .stream().filter(ns -> existingDividendsSplits.stream()
            .filter(es -> DateHelper.isSameDay(es.getEventDate(), ns.getEventDate())).findFirst().isEmpty())
        .peek(ns -> {
          ns.setCreateType(CreateType.CONNECTOR_CREATED);
          ns.setCreateModifyTime(new Date());
        }).collect(Collectors.toList());
    return jpaRepository.saveAll(canCreateSplits);
  }
 
  
}

package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.DividendSplit;
import grafioschtrader.entities.Security;
import grafioschtrader.types.CreateType;

public abstract class DividendSplitsHelper {

  public static <S extends DividendSplit> List<S> updateDividendSplitData(Security security, List<S> dividendSplitsRead,
      List<S> existingDividendsSplits, JpaRepository<S, Integer> jpaRepository) {

    List<S> canCreateDividendSplits = dividendSplitsRead.stream()
        .filter(ns -> existingDividendsSplits.stream()
            .filter(es -> DateHelper.isSameDay(es.getEventDate(), ns.getEventDate())).findFirst().isEmpty())
        .peek(ns -> {
          ns.setCreateType(CreateType.CONNECTOR_CREATED);
          ns.setCreateModifyTime(new Date());
        }).collect(Collectors.toList());
    return jpaRepository.saveAll(canCreateDividendSplits);
  }

}

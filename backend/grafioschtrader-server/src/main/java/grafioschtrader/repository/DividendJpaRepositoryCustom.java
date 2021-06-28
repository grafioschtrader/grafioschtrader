package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.Security;

public interface DividendJpaRepositoryCustom {

  List<String> periodicallyUpdate();

  List<String> loadAllDividendDataFromConnector(Security security);
}

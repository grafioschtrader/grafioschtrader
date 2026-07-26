package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;


@Suite
@SelectClasses({UserResourceTest.class, UserEntityChangeLimitRessourceTest.class, ImportTransactionPlatformResourceTest.class,
  TradingPlatformPlanResourceTest.class, StockexchangeResourceTest.class, TradingCalendarRuleSetResourceTest.class,
  AssetclassResourceTest.class, SecurityResourceTest.class, TaskDataChangeResourceTest.class})
public class ResoureTestSuite {

}

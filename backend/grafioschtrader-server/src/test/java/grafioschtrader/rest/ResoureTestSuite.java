package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;


@Suite
@SelectClasses({UserResourceTest.class, UserEntityChangeLimitRessourceTest.class, ImportTransactionPlatformResourceTest.class,
  TradingPlatformPlanResourceTest.class, StockexchangeResourceTest.class, AssetclassResourceTest.class, SecurityResourceTest.class})
public class ResoureTestSuite {

}
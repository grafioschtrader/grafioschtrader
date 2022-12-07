package grafioschtrader.rest;


import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({ DeleteALLDataTest.class, UserEntityChangeLimitRessourceTest.class, StockexchangeResourceTest.class,
    AssetclassResourceTest.class, SecurityResourceTest.class })
public class ResoureTestSuite {

}
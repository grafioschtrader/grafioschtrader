package grafioschtrader.rest;


import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
@SelectClasses({ DeleteALLDataTest.class, UserEntityChangeLimitRessourceTest.class, StockexchangeResourceTest.class,
    AssetclassResourceTest.class, SecurityResourceTest.class })
public class ResoureTestSuite {

}
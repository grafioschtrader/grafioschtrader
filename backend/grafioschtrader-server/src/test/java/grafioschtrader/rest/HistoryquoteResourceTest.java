package grafioschtrader.rest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)

class HistoryquoteResourceTest extends BaseIntegrationTest {
  

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTemplate, port, jwtTokenHandler);
  }

  @Test
  @Order(2)
  void test() {

  }

}

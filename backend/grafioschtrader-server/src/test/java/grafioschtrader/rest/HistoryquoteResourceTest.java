package grafioschtrader.rest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import grafioschtrader.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;

@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class HistoryquoteResourceTest {

  @Autowired
  TestRestTemplate restTemplate = new TestRestTemplate();

  @LocalServerPort
  private int port;

  @Autowired
  private JwtTokenHandler jwtTokenHandler;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTemplate, port, jwtTokenHandler);
  }

  @Test
  @Order(2)
  void test() {

  }

}

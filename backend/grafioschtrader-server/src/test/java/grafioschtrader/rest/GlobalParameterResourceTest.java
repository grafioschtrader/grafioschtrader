package grafioschtrader.rest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class GlobalParameterResourceTest {

  @Autowired
  TestRestTemplate restTemplate = new TestRestTemplate();

  @LocalServerPort
  private int port;

  @BeforeAll
  static void setUp() {
    System.out.println("Hugo");
  }

 @Test
  void testRequest() {
    ResponseEntity<List<ValueKeyHtmlSelectOptions>> vkhso = this.restTemplate.exchange(
        createURLWithPort("/api/globalparameters/locales"), HttpMethod.GET, null,
        new ParameterizedTypeReference<List<ValueKeyHtmlSelectOptions>>() {
        });

    assertThat(vkhso.getStatusCode(), is(HttpStatus.OK));
    System.out.println(vkhso.getBody());
  }

  @Test
  void testUserLogon() {

    String input = "{\"email\": \"hg@hugograf.com\", \"password\": \"a\", \"timezoneOffset\": -120}";
    HttpEntity<String> entity = new HttpEntity<>(input);

    ResponseEntity<String> response = restTemplate.exchange(createURLWithPort("/api/login"), HttpMethod.POST, entity,
        String.class);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    HttpHeaders headers = response.getHeaders();
    System.out.println(headers.get("x-auth-token"));
  }

  private String createURLWithPort(String uri) {
    return "http://localhost:" + port + uri;
  }

}

package grafioschtrader.rest;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;

import grafiosch.dto.ValueKeyHtmlSelectOptions;

class GlobalParameterResourceTest extends BaseIntegrationTest  {

  @Test
  void testRequest() {
    List<ValueKeyHtmlSelectOptions> body = restTestClient.get()
        .uri("/api/globalparameters/locales")
        .exchange()
        .expectStatus().isOk()
        .expectBody(new ParameterizedTypeReference<List<ValueKeyHtmlSelectOptions>>() {})
        .returnResult()
        .getResponseBody();

    Assertions.assertThat(body).isNotNull();
  }

  @Test
  void testUserLogon() {
    String input = "{\"email\": \"hg@hugograf.com\", \"password\": \"a\", \"timezoneOffset\": -120}";

    HttpHeaders responseHeaders = restTestClient.post()
        .uri("/api/login")
        .body(input)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseHeaders();

    Assertions.assertThat(responseHeaders.getFirst("x-auth-token")).isNotNull();
  }

}

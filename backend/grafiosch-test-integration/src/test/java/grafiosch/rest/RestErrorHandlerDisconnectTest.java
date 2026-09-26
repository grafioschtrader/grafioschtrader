package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.stream.Stream;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import grafiosch.rest.helper.RestErrorHandler;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.core.exc.JacksonIOException;

/** Verifies MVC exception selection for cancelled responses without starting an application or database. */
class RestErrorHandlerDisconnectTest {
  private final RestErrorHandler advice = new RestErrorHandler(new StaticMessageSource());
  private final FailingController controller = new FailingController();
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(advice).build();
  }

  @ParameterizedTest
  @MethodSource("disconnects")
  @DisplayName("Direct and Jackson-wrapped client disconnects produce no second response")
  void disconnectedResponseIsNotWrittenAgain(Exception failure) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    assertThat(advice.responseWriteException(failure, response)).isNull();
    verifyNoInteractions(response);

    controller.failure = failure;
    mvc.perform(get("/response-write-test")).andExpect(content().string(""));
  }

  @Test
  @DisplayName("A genuine JSON serialization failure remains an HTTP 500 error")
  void serializationFailureRemainsVisible() throws Exception {
    controller.failure = new HttpMessageNotWritableException("Cannot serialize result",
        new IllegalStateException("Invalid result value"));
    mvc.perform(get("/response-write-test")).andExpect(status().isInternalServerError())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid result value")));
  }

  @Test
  @DisplayName("Unrelated controller failures retain the existing HTTP 500 handling")
  void unrelatedFailureRemainsVisible() throws Exception {
    controller.failure = new IllegalArgumentException("Invalid calculation");
    mvc.perform(get("/response-write-test")).andExpect(status().isInternalServerError())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid calculation")));
  }

  static Stream<Exception> disconnects() {
    var aborted = new ClientAbortException(
        new IOException("Eine bestehende Verbindung wurde softwaregesteuert durch den Hostcomputer abgebrochen"));
    var unusable = new AsyncRequestNotUsableException("ServletOutputStream failed to write", aborted);
    return Stream.of(unusable,
        new HttpMessageNotWritableException("Could not write JSON", JacksonIOException.construct(unusable)));
  }

  @RestController
  static class FailingController {
    private Exception failure;

    @GetMapping("/response-write-test")
    public Object response() throws Exception {
      throw failure;
    }
  }
}

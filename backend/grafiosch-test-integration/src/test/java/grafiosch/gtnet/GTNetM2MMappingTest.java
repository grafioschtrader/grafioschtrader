package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import grafiosch.rest.GrafioschIntegrationTestContext;
import grafiosch.rest.RequestMappings;

/**
 * Guards the single GTNet M2M endpoint of this host.
 *
 * <p>
 * {@code GTNetM2MResource} lives in {@code grafiosch-server-base} so that every application on the libraries has it by
 * component scan alone. Two things can silently go wrong and neither is a compile error: an application re-introduces
 * its own copy, which makes Spring fail late with an ambiguous mapping or - worse - shadows the library behaviour; or
 * the endpoint is missing altogether, which turns every inbound peer message into a 404 that only shows up on the
 * sending side. Asserting the count is exactly one covers both.
 * </p>
 */
@GrafioschIntegrationTestContext
class GTNetM2MMappingTest {

  @Autowired
  private RequestMappingHandlerMapping requestMappingHandlerMapping;

  @Test
  @DisplayName("POST /m2m/gtnet is mapped exactly once")
  void m2mEndpointIsMappedExactlyOnce() {
    List<HandlerMethod> handlers = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
        .filter(entry -> matchesM2MGtNet(entry.getKey())).map(Map.Entry::getValue).toList();

    assertThat(handlers).as("handler methods mapped to %s", RequestMappings.GTNET_M2M_MAP).hasSize(1);
    assertThat(handlers.getFirst().getBeanType().getName()).isEqualTo("grafiosch.m2m.rest.GTNetM2MResource");
  }

  private boolean matchesM2MGtNet(RequestMappingInfo info) {
    return info.getPathPatternsCondition() != null
        && info.getPathPatternsCondition().getPatternValues().stream().anyMatch(RequestMappings.GTNET_M2M_MAP::equals);
  }
}

package grafioschtrader.m2m.client;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.ApplicationInfo;
import grafioschtrader.rest.RequestMappings;
import io.netty.resolver.ResolvedAddressTypes;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Service
public class BaseDataClient {


  public ApplicationInfo getActuatorInfo(String domainName) {
    return getWebClientForDomain(domainName).get().uri(uriBuilder -> uriBuilder
    .path(RequestMappings.ACTUATOR_MAP + "/info").build())
    .retrieve().bodyToMono(ApplicationInfo.class).block();
  }

  
  public void sendToMsg(String targetDomain, MessageEnvelope messageEnvelope) {
    getWebClientForDomain(targetDomain).post()
        .uri(uriBuilder -> uriBuilder.path(RequestMappings.GTNET_M2M_MAP).build())
        .body(Mono.just(messageEnvelope), MessageEnvelope.class).retrieve().bodyToMono(MessageEnvelope.class).block();
  }
  
  
  private WebClient getWebClientForDomain(String domainName) {
    HttpClient httpClient = HttpClient.create().resolver(spec -> {
      spec.resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED);
      spec.disableRecursionDesired(false);
    });

    return WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(httpClient))
    .baseUrl(domainName)
    .build();
  }
  
}

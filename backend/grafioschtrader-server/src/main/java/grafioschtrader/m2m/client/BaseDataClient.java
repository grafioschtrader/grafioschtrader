package grafioschtrader.m2m.client;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.model.msg.ApplicationInfo;
import grafioschtrader.rest.RequestMappings;

@Service
public class BaseDataClient {

  
  public Security getSecurityByIsinAndCurrency(String isin, String currency) {
    return WebClient
        .create("http://[2a02:aa14:a243:1080:b561:cf4c:ddf7:6d80]").get().uri(uriBuilder -> uriBuilder
            .path(RequestMappings.SECURITY_M2M_MAP + "/{isin}/{currency}").build(isin, currency))
        .retrieve().bodyToMono(Security.class).block();
  }

  public ApplicationInfo getActuatorInfo(String domainName) {
    return WebClient
        .create(domainName).get().uri(uriBuilder -> uriBuilder
            .path("actuator/info").build())
        .retrieve().bodyToMono(ApplicationInfo.class).block();
  }
  
}

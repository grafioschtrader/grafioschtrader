package grafioschtrader.m2m.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.m2m.model.MessageResponse;
import grafioschtrader.repository.GTNetMessageJpaRepository;
import grafioschtrader.rest.RequestMappings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.GTNET_M2M_MAP)
@Tag(name = RequestMappings.GTNET_M2M, description = "Controller for GTNet messages")
public class GTNetM2MResource {

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;
  
  @Operation(summary = "", description = "", tags = { RequestMappings.GTNET_M2M })
  @PostMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MessageResponse> receiveMessage(@Valid @RequestBody MessageEnvelope messageEnvelope) {

    return new ResponseEntity<>(gtNetMessageJpaRepository.getMsgResponse(messageEnvelope), HttpStatus.OK);
  }
}

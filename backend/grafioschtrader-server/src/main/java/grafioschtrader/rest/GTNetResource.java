package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.GTNet;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.repository.GTNetJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestMappings.GTNET_MAP)
@Tag(name = RequestMappings.GTNET, description = "Controller for gtnet")
public class GTNetResource extends UpdateCreateDeleteAudit<GTNet> {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Operation(summary = "Returns all existing GTNet entires", description = "", tags = { RequestMappings.GTNET })
  @GetMapping(value = "/gtnetwithmessage", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetWithMessages> getAllGTNetsWithMessages() {
    return new ResponseEntity<>(gtNetJpaRepository.getAllGTNetsWithMessages(), HttpStatus.OK);
  }

  @Operation(summary = "Client produces a message and wants to send it to other participants", description = "", tags = { RequestMappings.GTNET })
  @PostMapping(value = "/submitmsg", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetWithMessages> submitMsg(@Valid @RequestBody final MsgRequest msgRequest) {
    return new ResponseEntity<>(gtNetJpaRepository.submitMsg(msgRequest), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<GTNet> getUpdateCreateJpaRepository() {
    return gtNetJpaRepository;
  }
  
}

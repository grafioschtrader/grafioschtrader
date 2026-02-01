package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.model.GTNetWithMessages;
import grafiosch.gtnet.model.MsgRequest;
import grafiosch.gtnet.model.MultiTargetMsgRequest;
import grafiosch.repository.GTNetJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestMappings.GTNET_MAP)
@Tag(name = RequestMappings.GTNET, description = "Controller for gtnet")
public class GTNetResource extends UpdateCreateResource<GTNet> {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Operation(summary = "Returns all existing GTNet entires", description = "", tags = { RequestMappings.GTNET })
  @GetMapping(value = "/gtnetwithmessage", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetWithMessages> getAllGTNetsWithMessages() {
    return new ResponseEntity<>(gtNetJpaRepository.getAllGTNetsWithMessages(), HttpStatus.OK);
  }

  @Operation(summary = "Client produces a message and wants to send it to other participants", description = "", tags = {
      RequestMappings.GTNET })
  @PostMapping(value = "/submitmsg", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetWithMessages> submitMsg(@Valid @RequestBody final MsgRequest msgRequest) {
    return new ResponseEntity<>(gtNetJpaRepository.submitMsg(msgRequest), HttpStatus.OK);
  }

  @Operation(summary = "Client sends an admin message to multiple selected targets", description = "Creates one message and queues delivery to all selected targets via background job", tags = {
      RequestMappings.GTNET })
  @PostMapping(value = "/submitmsgmulti", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetWithMessages> submitMsgToMultiple(
      @Valid @RequestBody final MultiTargetMsgRequest multiTargetMsgRequest) {
    return new ResponseEntity<>(gtNetJpaRepository.submitMsgToMultiple(multiTargetMsgRequest), HttpStatus.OK);
  }

  @Operation(summary = "Returns messages for a specific GTNet domain (lazy loading)", description = "Used when expanding a row in the GTNet setup table", tags = {
      RequestMappings.GTNET })
  @GetMapping(value = "/messages/{idGtNet}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GTNetMessage>> getMessagesByIdGtNet(@PathVariable Integer idGtNet) {
    return new ResponseEntity<>(gtNetJpaRepository.getMessagesByIdGtNet(idGtNet), HttpStatus.OK);
  }

  @Operation(summary = "Deletes a batch of GTNet messages", description = "Validates that all messages are deletable and cascade-deletes responses", tags = {
      RequestMappings.GTNET })
  @PostMapping(value = "/deletemessagebatch", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteMessageBatch(@RequestBody List<Integer> idGtNetMessageList) {
    gtNetJpaRepository.deleteMessageBatch(idGtNetMessageList);
    return ResponseEntity.ok().build();
  }

  @Override
  protected UpdateCreateJpaRepository<GTNet> getUpdateCreateJpaRepository() {
    return gtNetJpaRepository;
  }

}

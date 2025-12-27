package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dynamic.model.ClassDescriptorInputAndShow;
import grafiosch.rest.UpdateCreateDeleteAudit;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetModelHelper;
import grafioschtrader.repository.GTNetMessageJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping(RequestGTMappings.GTNET_MESSAGE_MAP)
@Tag(name = RequestGTMappings.GTNET_MESSAGE, description = "Controller for gtnet message")
public class GTNetMessageResource extends UpdateCreateDeleteAudit<GTNetMessage> {

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Operation(summary = "Returns all form defintion of messages", description = "", tags = {
      RequestGTMappings.GTNET_MESSAGE })
  @GetMapping(value = "/msgformdefinition", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<GTNetMessageCodeType, ClassDescriptorInputAndShow>> getAllFormDefinitions() {
    return new ResponseEntity<>(GTNetModelHelper.getAllFormDefinitionsWithClass(), HttpStatus.OK);
  }

  @Operation(summary = "Marks a message as read", description = "Sets the hasBeenRead flag to true for the specified message", tags = {
      RequestGTMappings.GTNET_MESSAGE })
  @PatchMapping(value = "/{idGtNetMessage}/markasread")
  @Transactional
  public ResponseEntity<Void> markAsRead(@PathVariable Integer idGtNetMessage) {
    gtNetMessageJpaRepository.markAsRead(idGtNetMessage);
    return ResponseEntity.ok().build();
  }

  @Override
  protected UpdateCreateJpaRepository<GTNetMessage> getUpdateCreateJpaRepository() {
    return gtNetMessageJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}

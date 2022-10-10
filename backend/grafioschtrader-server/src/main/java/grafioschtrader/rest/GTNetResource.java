package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository.GTNetWithMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.GTNET_MAP)
@Tag(name = RequestMappings.GTNET, description = "Controller for gtnet")
public class GTNetResource {
   
  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;
    
  @Operation(summary = "Returns all existing GTNet entires", description = "", tags = {
      RequestMappings.GTNET })
  @GetMapping(value = "/gtnetwithmessage", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTNetWithMessages> getAllGTNetsWithMessages() {
    return new ResponseEntity<>(gtNetJpaRepository.getAllGTNetsWithMessages(), HttpStatus.OK);
  }
}

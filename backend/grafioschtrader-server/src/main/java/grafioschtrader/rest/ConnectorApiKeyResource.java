package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.ConnectorApiKey;
import grafioschtrader.repository.ConnectorApiKeyJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.CONNECTOR_API_KEY_MAP)
@Tag(name = RequestMappings.CONNECTOR_API_KEY, description = "Controller for connector api key")
public class ConnectorApiKeyResource {

  
  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;
  
  @Operation(summary = "Create a property value of existing connector api key", description = "Only admin can create connector api key", tags = {
      RequestMappings.CONNECTOR_API_KEY })
  @PostMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ConnectorApiKey> createConnectorApiKey(
      @Valid @RequestBody final ConnectorApiKey connectorApiKey) {
    return new ResponseEntity<>(connectorApiKeyJpaRepository.saveOnlyAttributes(connectorApiKey), HttpStatus.OK);
  }
  
  
  @Operation(summary = "Update connector api key", description = "Only admin can change connector api key", tags = {
      RequestMappings.CONNECTOR_API_KEY })
  @PutMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ConnectorApiKey> updateConnectorApiKey(
      @Valid @RequestBody final ConnectorApiKey connectorApiKey) {
    return new ResponseEntity<>(connectorApiKeyJpaRepository.saveOnlyAttributes(connectorApiKey), HttpStatus.OK);
  }
}

package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.ConnectorApiKey;
import grafioschtrader.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.repository.ConnectorApiKeyJpaRepositoryImpl.SubscriptionTypeReadableName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestMappings.CONNECTOR_API_KEY_MAP)
@Tag(name = RequestMappings.CONNECTOR_API_KEY, description = "Controller for connector api key")
public class ConnectorApiKeyResource {
  
  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  @Operation(summary = "Return of all existing API keys from data provider", description = "Only for admin", tags = {
      RequestMappings.CONNECTOR_API_KEY })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ConnectorApiKey>> getAllConnectorApiKey() {
    return new ResponseEntity<>(connectorApiKeyJpaRepository.findAll(), HttpStatus.OK);
  }
  
  @Operation(summary = "Return of the possible variants of data providers to subscriptions", description = "Only for admin", tags = {
      RequestMappings.CONNECTOR_API_KEY })
  @GetMapping(value = "/subscriptiontypeconnector", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, SubscriptionTypeReadableName>> getFeedSubscriptionType() {
    return new ResponseEntity<>(connectorApiKeyJpaRepository.getFeedSubscriptionType(), HttpStatus.OK);
  }
  
  @Operation(summary = "Add a connector api key with its data provider", description = "Only admin can create connector api key", tags = {
      RequestMappings.CONNECTOR_API_KEY })
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ConnectorApiKey> createConnectorApiKey(
      @Valid @RequestBody final ConnectorApiKey connectorApiKey) {
    return new ResponseEntity<>(connectorApiKeyJpaRepository.saveOnlyAttributes(connectorApiKey), HttpStatus.OK);
  }
    
  @Operation(summary = "Update connector api key", description = "Only admin can change connector api key", tags = {
      RequestMappings.CONNECTOR_API_KEY })
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ConnectorApiKey> updateConnectorApiKey(
      @Valid @RequestBody final ConnectorApiKey connectorApiKey) {
    return new ResponseEntity<>(connectorApiKeyJpaRepository.saveOnlyAttributes(connectorApiKey), HttpStatus.OK);
  }
  
  @Operation(summary = "Delete a connector api key", description = "Only admin can delete connector api key")
  @DeleteMapping(value = "/{idProvider}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteConnectorApiKey(@PathVariable final String idProvider) {
    connectorApiKeyJpaRepository.deleteConnectorApiKeyByIdProvider(idProvider);
    return ResponseEntity.noContent().build();
  }
}

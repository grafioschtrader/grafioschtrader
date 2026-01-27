package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.repository.GTNetSecurityImpHeadJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for managing GTNet security import headers. Provides CRUD operations
 * for organizing security import batches.
 */
@RestController
@RequestMapping(RequestGTMappings.GTNETSECURITYIMPHEAD_MAP)
@Tag(name = RequestGTMappings.GTNETSECURITYIMPHEAD, description = "Controller for GTNet Security Import Headers")
public class GTNetSecurityImpHeadResource extends UpdateCreateDeleteWithTenantResource<GTNetSecurityImpHead> {

  @Autowired
  private GTNetSecurityImpHeadJpaRepository gtNetSecurityImpHeadJpaRepository;

  public GTNetSecurityImpHeadResource() {
    super(GTNetSecurityImpHead.class);
  }

  @Operation(summary = "Get all import headers for the current tenant",
      description = "Returns all GTNet security import headers belonging to the authenticated user's tenant.",
      tags = { RequestGTMappings.GTNETSECURITYIMPHEAD })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GTNetSecurityImpHead>> getAllByTenant() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(gtNetSecurityImpHeadJpaRepository.findByIdTenant(user.getIdTenant()), HttpStatus.OK);
  }

  @Operation(summary = "Queue background job to import securities from GTNet",
      description = """
          Creates a background task to query GTNet peers and create securities for all positions without
          a linked security. If a job is already pending for this import header, no new job is created.""",
      tags = { RequestGTMappings.GTNETSECURITYIMPHEAD })
  @PostMapping(value = "/{idGtNetSecurityImpHead}/importjob", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> queueImportJob(@PathVariable Integer idGtNetSecurityImpHead) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    boolean queued = gtNetSecurityImpHeadJpaRepository.queueImportJobIfNotExists(
        idGtNetSecurityImpHead, user.getIdTenant());

    Map<String, Object> response = new HashMap<>();
    response.put("queued", queued);
    response.put("idGtNetSecurityImpHead", idGtNetSecurityImpHead);

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<GTNetSecurityImpHead> getUpdateCreateJpaRepository() {
    return gtNetSecurityImpHeadJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }
}

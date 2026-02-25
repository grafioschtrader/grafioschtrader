package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.entities.StandingOrder;
import grafioschtrader.entities.StandingOrderFailure;
import grafioschtrader.repository.StandingOrderFailureJpaRepository;
import grafioschtrader.repository.StandingOrderJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for standing order CRUD operations. Handles the polymorphic StandingOrder hierarchy
 * (StandingOrderCashaccount and StandingOrderSecurity) via Jackson @JsonTypeInfo.
 */
@RestController
@RequestMapping(RequestGTMappings.STANDINGORDER_MAP)
@Tag(name = RequestGTMappings.STANDINGORDER, description = "Controller for recurring standing orders")
public class StandingOrderResource extends UpdateCreateDeleteWithTenantResource<StandingOrder> {

  @Autowired
  private StandingOrderJpaRepository standingOrderJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private StandingOrderFailureJpaRepository standingOrderFailureJpaRepository;

  public StandingOrderResource() {
    super(StandingOrder.class);
  }

  @Operation(summary = "Returns all standing orders for the current tenant",
      description = "Returns both cashaccount and security standing orders for the logged-in user's tenant, "
          + "enriched with hasTransactions and failureCount transient fields.",
      tags = {RequestGTMappings.STANDINGORDER})
  @GetMapping(value = "/tenant", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<StandingOrder>> getAllForTenant() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<StandingOrder> orders = standingOrderJpaRepository.findByIdTenant(user.getIdTenant());
    orders.forEach(so -> so.setHasTransactions(
        transactionJpaRepository.countByIdStandingOrder(so.getIdStandingOrder()) > 0));

    // Batch-load failure counts
    List<Integer> ids = orders.stream().map(StandingOrder::getIdStandingOrder).collect(Collectors.toList());
    if (!ids.isEmpty()) {
      Map<Integer, Long> failureCounts = standingOrderFailureJpaRepository.countByStandingOrderIds(ids).stream()
          .collect(Collectors.toMap(row -> (Integer) row[0], row -> (Long) row[1]));
      orders.forEach(so -> so.setFailureCount(failureCounts.getOrDefault(so.getIdStandingOrder(), 0L).intValue()));
    }

    return new ResponseEntity<>(orders, HttpStatus.OK);
  }

  @Operation(summary = "Returns execution failures for a specific standing order",
      description = "Returns all persisted failure records for the given standing order, newest first. "
          + "Only accessible if the standing order belongs to the current user's tenant.",
      tags = {RequestGTMappings.STANDINGORDER})
  @GetMapping(value = "/{idStandingOrder}/failures", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<StandingOrderFailure>> getFailures(@PathVariable Integer idStandingOrder) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    // Tenant ownership check
    StandingOrder so = standingOrderJpaRepository.findById(idStandingOrder).orElse(null);
    if (so == null || !so.getIdTenant().equals(user.getIdTenant())) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    return new ResponseEntity<>(
        standingOrderFailureJpaRepository.findByIdStandingOrderOrderByExecutionDateDesc(idStandingOrder),
        HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<StandingOrder> getUpdateCreateJpaRepository() {
    return standingOrderJpaRepository;
  }
}

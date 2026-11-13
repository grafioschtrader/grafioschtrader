package grafiosch.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.entities.User;
import grafiosch.repository.UDFMetadataSubscriptionJpaRepository;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;

@RestController
@RequestMapping(RequestMappings.UDF_METADATA_SUBSCRIPTION_MAP)
public class UDFMetadataSubscriptionResource extends UpdateCreateDeleteWithUserIdResource<UDFMetadataSubscription> {
  private final UDFMetadataSubscriptionJpaRepository repository;
  private final DailyLimitService dailyLimit;

  public UDFMetadataSubscriptionResource(UDFMetadataSubscriptionJpaRepository repository,
      DailyLimitService dailyLimit) {
    super(UDFMetadataSubscription.class);
    this.repository = repository;
    this.dailyLimit = dailyLimit;
  }

  @Override
  protected UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataSubscription> getUpdateCreateJpaRepository() {
    return repository;
  }

  @Override
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteResource(@PathVariable Integer id) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    dailyLimit.check(user, UDFMetadataSubscription.class.getSimpleName(), 1);
    return super.deleteResource(id);
  }

  @PostMapping("/{id}/claim")
  public ResponseEntity<Void> claim(@PathVariable Integer id) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    dailyLimit.check(user, UDFMetadataSubscription.class.getSimpleName(), 1);
    repository.claimOwnership(id, user);
    dailyLimit.log(user.getIdUser(), UDFMetadataSubscription.class.getSimpleName(), OperationType.UPDATE, 1);
    return ResponseEntity.noContent().build();
  }
}

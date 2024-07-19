package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.User;
import grafioschtrader.entities.UserBaseID;
import grafioschtrader.types.OperationType;
import io.swagger.v3.oas.annotations.Operation;

public abstract class UpdateCreateDeleteWithUserIdResource<T extends UserBaseID> extends UpdateCreateResource<T> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private final Class<T> type;

  @Override
  protected abstract UpdateCreateDeleteWithUserIdJpaRepository<T> getUpdateCreateJpaRepository();

  UpdateCreateDeleteWithUserIdResource(Class<T> type) {
    this.type = type;
  }
  
  @Operation(summary = "Delete an information object by its ID and the user ID.", description = "")
  @DeleteMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer id) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    log.debug("Delete by id : {}", id);
    int countDel =  getUpdateCreateJpaRepository().delEntityWithUserId(id, user.getIdUser());
    if(countDel != 1) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    } else {
      this.logAddUpdDel(user.getIdUser(), type.getSimpleName(), OperationType.DELETE);  
    }
    return ResponseEntity.noContent().build();
  }
}

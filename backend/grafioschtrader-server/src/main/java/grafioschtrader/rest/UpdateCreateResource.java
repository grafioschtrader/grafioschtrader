package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import grafiosch.entities.BaseID;
import jakarta.validation.Valid;

/**
 * A REST API that supports the creation and editing of an entity. In addition,
 * these edits are summed and stored per information class on a daily basis.
 * Attention. If the deletion is additionally implemented specifically, there is
 * no summation deletion.
 *
 * @param <T>
 */
public abstract class UpdateCreateResource<T extends BaseID> extends UpdateCreate<T> {

  /**
   * Request for a new entity. The new URI-location of the created record is not
   * returned.
   */
  @Override
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<T> create(@Valid @RequestBody T entity) throws Exception {
    return createEntity(entity);
  }

  /**
   * Request for update entity.
   */
  @Override
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<T> update(@Valid @RequestBody final T entity) throws Exception {
    return updateEntity(entity);
  }

}

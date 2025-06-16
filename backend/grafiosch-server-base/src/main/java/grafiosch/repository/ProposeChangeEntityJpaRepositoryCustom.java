package grafiosch.repository;

import java.util.List;

import grafiosch.entities.ProposeChangeEntity;
import grafiosch.repository.ProposeChangeEntityJpaRepositoryImpl.ProposeChangeEntityWithEntity;

/**
 * Custom repository interface for managing entity change proposals. Provides specialized operations for retrieving and
 * processing change requests submitted by users for shared entities that require administrative approval.
 * 
 * This interface extends the base repository functionality to handle the workflow of entity modification proposals,
 * including the comparison between current entity states and proposed changes.
 */
public interface ProposeChangeEntityJpaRepositoryCustom extends BaseRepositoryCustom<ProposeChangeEntity> {
  /**
   * Retrieves all pending entity change proposals with their corresponding current and proposed entity states. This
   * method creates a comprehensive view for administrators to review proposed changes by:</br>
   * - Fetching all open change proposals based on user privileges</br>
   * - Loading the current state of each affected entity</br>
   * - Applying proposed changes to create the modified entity version</br>
   * - Cleaning up orphaned proposals where the target entity no longer exists</br>
   * 
   * The returned data structure contains three components for each proposal: the original proposal request, the current
   * entity state, and the entity state with proposed changes applied, enabling side-by-side comparison.</br>
   * 
   * Access control is applied based on the current user's role:</br>
   * - Users with higher privileges see all pending proposals</br>
   * - Regular users only see proposals for entities they own</br>
   *
   * @return list of proposal containers with current and proposed entity states
   * @throws Exception if entity class resolution fails or proposal processing encounters errors
   */
  List<ProposeChangeEntityWithEntity> getProposeChangeEntityWithEntity() throws Exception;
}

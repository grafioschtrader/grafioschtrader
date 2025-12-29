package grafiosch.repository;

import grafiosch.dto.TaskDataChangeFormConstraints;

/**
 * Provider interface for adding entity ID options to task form constraints. Implementations can add selectable options
 * for entity types that have predefined values (e.g., connectors with known IDs).
 *
 * <p>
 * This interface allows modules to extend the task form with their own entity options without modifying the base
 * implementation.
 * </p>
 */
public interface EntityIdOptionsProvider {

  /**
   * Adds entity ID options to the given form constraints. Implementations should populate the
   * {@link TaskDataChangeFormConstraints#entityIdOptions} map with options for entity types they support.
   *
   * @param constraints the form constraints to populate with entity ID options
   */
  void addEntityIdOptions(TaskDataChangeFormConstraints constraints);
}

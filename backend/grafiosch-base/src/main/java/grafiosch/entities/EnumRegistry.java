package grafiosch.entities;

import java.util.ArrayList;
import java.util.List;

import grafiosch.types.IBaseEnum;

/**
 * A generic registry for enum types implementing IBaseEnum. This registry works for any enum that implements IBaseEnum,
 * such as ITaskType or IUDFSpecialType.
 *
 * @param <T> the enum type which must implement IBaseEnum
 */
public class EnumRegistry<S, T extends IBaseEnum<S>> {

  // List to store the enum types
  private final List<T> types;

  public EnumRegistry() {
    this.types = new ArrayList<>();
  }

  /**
   * Constructs a new registry with the specified initial enum types.
   *
   * @param initialTypes an array of enum types to initialize the registry
   * @throws IllegalStateException if two of the supplied types share the same value
   */
  public EnumRegistry(T[] initialTypes) {
    this.types = new ArrayList<>();
    addTypes(initialTypes);
  }

  /**
   * Adds new enum types to the registry. Each value must be unique across all types already
   * registered, otherwise {@link #getTypeByValue(Object)} would silently resolve a value to the first
   * registered type and mask the conflict. This guard fails fast on a cross-enum value collision (for
   * example when a base enum and an application enum are merged into the same registry).
   *
   * @param newTypes an array of enum types to be added
   * @throws IllegalStateException if a type's value duplicates one that is already registered
   */
  public void addTypes(T[] newTypes) {
    for (T newType : newTypes) {
      T existing = getTypeByValue(newType.getValue());
      if (existing != null) {
        throw new IllegalStateException(String.format(
            "Duplicate value %s in enum registry: %s collides with already registered %s",
            newType.getValue(), ((Enum<?>) newType).name(), ((Enum<?>) existing).name()));
      }
      types.add(newType);
    }
  }

  /**
   * Retrieves an enum type by its byte value.
   *
   * @param value the byte value to search for
   * @return the enum type with the matching byte value, or null if not found
   */
  public T getTypeByValue(S value) {
    return types.stream().filter(type -> type.getValue().equals(value)).findFirst().orElse(null);
  }

  /**
   * Retrieves an enum type by its name (case-insensitive).
   *
   * @param name the name of the enum type to search for
   * @return the enum type with the matching name, or null if not found
   */
  public T getTypeByName(String name) {
    return types.stream().filter(taskType -> ((Enum<?>) taskType).name().equalsIgnoreCase(name)).findFirst()
        .orElse(null);
  }
}
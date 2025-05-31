package grafiosch.types;

/**
 * Only need this if two enums are to be combined into one.
 */
public interface IBaseEnum<T> {
  public T getValue();

  public Enum<?>[] getValues();
}

package grafioschtrader.udffill.resolver;

import java.util.List;
import java.util.Set;

import grafiosch.types.UDFDataType;
import grafioschtrader.dto.UDFFillArgumentOption;
import grafioschtrader.entities.Security;

/**
 * A built-in lookup of the fill source {@code RESOLVER} against an external, keyed source. The fill rule of such a
 * field is {@code <resolverId>} optionally followed by a space and an argument, for example {@code WIKIPEDIA de,en}.
 * Implementations are Spring beans; every request to a public source goes through {@code UDFFillHttp}.
 */
public interface IUDFValueResolver {

  /**
   * Returns the ID stored in the fill rule. It is also the NLS key of the name of the resolver.
   */
  String getResolverId();

  /**
   * Returns the data types of a field this resolver can fill.
   */
  Set<UDFDataType> getSupportedDataTypes();

  /**
   * Returns the arguments this resolver accepts when they form a fixed list, so the edit form offers a selection. The
   * text of each entry is the NLS key {@code <resolverId>_<argument>}. The default is empty: the argument is a free
   * text that {@link #normalizeArgument(String)} validates.
   */
  default List<UDFFillArgumentOption> getArgumentOptions() {
    return List.of();
  }

  /**
   * Tells whether a field of the data type can be filled with the normalised argument. A resolver whose arguments
   * differ in the kind of value they return, for example a number and a yes or no, narrows the data types here; the
   * default accepts every data type of {@link #getSupportedDataTypes()}.
   *
   * @param udfDataType the data type of the field
   * @param argument    the normalised argument
   * @return true when the combination can be saved
   */
  default boolean supportsDataType(UDFDataType udfDataType, String argument) {
    return getSupportedDataTypes().contains(udfDataType);
  }

  /**
   * Returns the argument used when the rule names the resolver only.
   */
  String getDefaultArgument();

  /**
   * Normalises an argument for storing.
   *
   * @param argument the argument as typed, may be blank
   * @return the normalised argument
   * @throws IllegalArgumentException with an NLS key as message when the argument is invalid
   */
  String normalizeArgument(String argument);

  /**
   * Looks up the value for a security. Must be called outside any transaction; may make HTTP requests.
   *
   * @param security the security
   * @param argument the normalised argument
   * @return the value, a definitive "none" with its reason, or a technical failure
   */
  ResolveOutcome resolve(Security security, String argument);
}

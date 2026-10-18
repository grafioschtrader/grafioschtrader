package grafioschtrader.udffill.resolver;

import java.util.Set;

import grafiosch.types.UDFDataType;
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

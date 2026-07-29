package grafiosch.nls;

import java.io.Serial;
import java.util.List;

/**
 * Raised when two distinct raw property keys map to the same client key, so one of the two texts would silently replace
 * the other in the delivered payload.
 *
 * <p>
 * The condition is checked by a build-time guard rather than only at runtime, because the loser of such a collision is
 * decided by iteration order and the symptom is a wrong but plausible label rather than an error.
 * </p>
 */
public class NlsMappingCollisionException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final transient List<String> collisions;

  /**
   * @param collisions one human-readable line per collision, each naming the client key and the colliding raw keys
   */
  public NlsMappingCollisionException(List<String> collisions) {
    super("NLS key mapping is not unique, " + collisions.size() + " collision(s):"
        + System.lineSeparator() + String.join(System.lineSeparator(), collisions));
    this.collisions = List.copyOf(collisions);
  }

  /**
   * @return the individual collision descriptions, for a guard that wants to report them itself
   */
  public List<String> getCollisions() {
    return collisions;
  }
}

package grafiosch.gtnet;

import java.util.List;

/**
 * Visibility levels for GTNet admin messages.
 *
 * Controls who can see a message in the GTNet messaging system:
 * <ul>
 *   <li>{@link #ALL_USERS} - Message is visible to all users of the system</li>
 *   <li>{@link #ADMIN_ONLY} - Message is visible only to administrators</li>
 * </ul>
 *
 * Thread visibility rules:
 * <ul>
 *   <li>New thread: Admin can choose visibility (ALL_USERS or ADMIN_ONLY)</li>
 *   <li>Reply to ADMIN_ONLY thread: Forced to ADMIN_ONLY (cannot downgrade)</li>
 *   <li>Reply to ALL_USERS thread: Can be ALL_USERS or ADMIN_ONLY (can upgrade)</li>
 * </ul>
 */
public enum MessageVisibility {

  /**
   * Message is visible to all users of the system.
   */
  ALL_USERS((byte) 0),

  /**
   * Message is visible only to administrators.
   */
  ADMIN_ONLY((byte) 1);

  private final byte value;

  MessageVisibility(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  /**
   * The visibility values a reader of the given role may see. An administrator sees both, everybody else sees only
   * {@link #ALL_USERS}. Returned as byte values so it can be passed straight into an {@code IN} clause.
   *
   * @param isAdmin whether the reader holds the administrator role
   * @return the visibility values that reader may see, never empty
   */
  public static List<Byte> visibleTo(boolean isAdmin) {
    return isAdmin ? List.of(ALL_USERS.getValue(), ADMIN_ONLY.getValue()) : List.of(ALL_USERS.getValue());
  }

  /**
   * Gets the MessageVisibility enum constant for a given byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding MessageVisibility, or ALL_USERS if not found
   */
  public static MessageVisibility getByValue(byte value) {
    for (MessageVisibility visibility : values()) {
      if (visibility.getValue() == value) {
        return visibility;
      }
    }
    return ALL_USERS;
  }
}

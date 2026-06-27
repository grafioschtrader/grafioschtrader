package grafiosch.exceptions;

/**
 * Indicates that an entity referenced by a client request does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final Integer id;

  public ResourceNotFoundException(Integer id) {
    this.id = id;
  }

  public Integer getId() {
    return id;
  }
}

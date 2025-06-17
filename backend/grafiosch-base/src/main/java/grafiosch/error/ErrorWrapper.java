package grafiosch.error;

/**
 * Generic wrapper for error objects with type identification.
 * 
 * <p>
 * This class wraps error objects with their class name for JSON serialization in REST API responses. It allows clients
 * to identify the specific error type while preserving the original error object structure.
 * </p>
 * 
 * <h3>Features:</h3>
 * <ul>
 * <li><strong>Type Identification:</strong> Includes error class name for client processing</li>
 * <li><strong>Error Preservation:</strong> Maintains original error object structure</li>
 * <li><strong>JSON Serialization:</strong> Compatible with REST API response formatting</li>
 * </ul>
 * 
 * <h3>Usage:</h3>
 * <p>
 * Used by error handlers to wrap different error types while providing type information for client-side error
 * processing and debugging.
 * </p>
 */
public class ErrorWrapper {
  /**
   * Simple class name of the wrapped error object.
   * 
   * <p>
   * Provides type identification for the error without exposing full package paths, helping clients determine
   * appropriate error handling.
   * </p>
   */
  public String className;
  /**
   * The original error object being wrapped.
   * 
   * <p>
   * Contains the complete error object with all its properties and data, preserved for client consumption and error
   * analysis.
   * </p>
   */
  public Object error;

  /**
   * Creates an error wrapper with automatic class name extraction.
   * 
   * <p>
   * Wraps the provided error object and automatically extracts its simple class name for type identification in API
   * responses.
   * </p>
   * 
   * @param error the error object to wrap and identify
   */
  public ErrorWrapper(Object error) {
    this.className = error.getClass().getSimpleName();
    this.error = error;
  }

}

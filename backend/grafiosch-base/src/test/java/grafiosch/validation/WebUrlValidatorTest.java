package grafiosch.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintValidatorContext;

public class WebUrlValidatorTest {

  private WebUrlValidator validator;
  
  // A dummy ConstraintValidatorContext can be null since it is not used in the implementation.
  private ConstraintValidatorContext context = null;

  @BeforeEach
  public void setUp() {
    validator = new WebUrlValidator();
  }

  @Test
  public void testNullValue() {
    // Null should be considered valid.
    assertTrue(validator.isValid(null, context));
  }

  @Test
  public void testEmptyString() {
    // Empty string should be considered valid.
    assertTrue(validator.isValid("", context));
  }

  @Test
  public void testValidHttpUrl() {
    // A basic http URL.
    String url = "http://www.example.com";
    assertTrue(validator.isValid(url, context));
  }
  
  @Test
  public void testValidHttpsUrlWithPort() {
    // HTTPS with a port number.
    String url = "https://www.example.com:8080";
    assertTrue(validator.isValid(url, context));
  }

  @Test
  public void testValidHttpsUrlWithPath() {
    // HTTPS with a path.
    String url = "https://www.example.com/path/resource";
    assertTrue(validator.isValid(url, context));
  }
  
  @Test
  public void testValidFtpUrl() {
    // FTP URL.
    String url = "ftp://ftp.example.com";
    assertTrue(validator.isValid(url, context));
  }

  @Test
  public void testInvalidUrlMissingProtocol() {
    // Missing the required "//" in the protocol part.
    String url = "www.example.com";
    assertFalse(validator.isValid(url, context));
  }
  
  @Test
  public void testInvalidUrlMalformed() {
    // Malformed protocol (only one slash instead of two)
    String url = "http:/example.com";
    assertFalse(validator.isValid(url, context));
  }
  
  @Test
  public void testInvalidUrlPrivateIp() {
    // Private IP addresses (e.g., 192.168.x.x) are excluded by the regex.
    String url = "http://192.168.1.1";
    assertFalse(validator.isValid(url, context));
  }
}

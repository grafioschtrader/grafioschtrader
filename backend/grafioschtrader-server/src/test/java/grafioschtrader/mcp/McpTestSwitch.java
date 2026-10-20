package grafioschtrader.mcp;

import org.springframework.jdbc.core.JdbcTemplate;

import grafiosch.entities.Globalparameters;
import grafioschtrader.GlobalParamKeyDefault;
import jakarta.persistence.EntityManagerFactory;

/**
 * Opens and closes the MCP server for a test class through the two global parameters, as an administrator does. The
 * rows are written with JDBC, so the second-level cache of {@link Globalparameters} is evicted afterwards; otherwise
 * the running server would keep answering with the old value.
 */
public final class McpTestSwitch {

  private McpTestSwitch() {
  }

  /**
   * @param publicBaseUrl the public base URL to set, or null for {@code none}
   */
  public static void open(JdbcTemplate jdbcTemplate, EntityManagerFactory emf, String publicBaseUrl) {
    set(jdbcTemplate, emf, 1, publicBaseUrl);
  }

  /** Restores the state of a fresh installation: closed and without a public base URL. */
  public static void close(JdbcTemplate jdbcTemplate, EntityManagerFactory emf) {
    set(jdbcTemplate, emf, 0, null);
  }

  public static void set(JdbcTemplate jdbcTemplate, EntityManagerFactory emf, int use, String publicBaseUrl) {
    jdbcTemplate.update("UPDATE globalparameters SET property_int = ? WHERE property_name = ?", use,
        GlobalParamKeyDefault.GLOB_KEY_MCP_USE);
    jdbcTemplate.update("UPDATE globalparameters SET property_string = ? WHERE property_name = ?",
        publicBaseUrl == null ? GlobalParamKeyDefault.MCP_PUBLIC_BASE_URL_NONE : publicBaseUrl,
        GlobalParamKeyDefault.GLOB_KEY_MCP_PUBLIC_BASE_URL);
    emf.getCache().evict(Globalparameters.class);
  }
}

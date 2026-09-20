package grafioschtrader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads one fenced block out of a Flyway script.
 *
 * <p>
 * A migration that carries changes for more than one concern fences each of them with {@code -- SECTION-BEGIN <name>}
 * and {@code -- SECTION-END <name>}. The opt-in JDBC tests execute such a block against privately named scratch tables,
 * renaming every table the block mentions. Slicing from a marker to the end of the file used to be enough while each
 * concern had its own script; after two scripts were merged it is not, because the tail of the file then holds
 * statements the extracting test does not rename and would therefore run against the real {@code grafioschtrader_t}
 * tables. Extraction is bounded here so that a later addition to the script cannot silently widen what a test executes.
 * </p>
 */
public final class MigrationSections {

  private MigrationSections() {
  }

  /**
   * Returns the content between the fences of one section, without the fence lines themselves.
   *
   * @param migration path of the Flyway script, relative to the module directory
   * @param name      name used on both fence lines
   * @return the statements inside the fence
   * @throws IOException           if the script cannot be read
   * @throws IllegalStateException if either fence is missing or they appear in the wrong order
   */
  public static String read(String migration, String name) throws IOException {
    return readFrom(Files.readString(Path.of(migration)), name, migration);
  }

  /**
   * Returns the content between the fences of one section of an already loaded script. Same contract as
   * {@link #read(String, String)}, for a caller that reads the script from the class path rather than from the module
   * directory.
   *
   * @param script the whole script
   * @param name   name used on both fence lines
   * @param origin what the script is, used in the failure message only
   * @return the statements inside the fence
   * @throws IllegalStateException if either fence is missing or they appear in the wrong order
   */
  public static String readFrom(String script, String name, String origin) {
    String begin = "-- SECTION-BEGIN " + name;
    String end = "-- SECTION-END " + name;
    int from = script.indexOf(begin);
    int to = script.indexOf(end);
    if (from < 0 || to < from) {
      throw new IllegalStateException("Section '" + name + "' is not fenced in " + origin);
    }
    return script.substring(from + begin.length(), to);
  }
}

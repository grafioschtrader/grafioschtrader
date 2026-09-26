package grafioschtrader.common;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

/** Shared single-document syntax contract for user-authored YAML. Never constructs arbitrary Java objects. */
public final class StrictYaml {
  private StrictYaml() {
  }

  /** Validates the complete input, including content after the first document. Blank optional values are allowed. */
  public static void validate(String text) {
    if (text == null || text.isBlank())
      return;
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(0);
    options.setNestingDepthLimit(30);
    options.setCodePointLimit(1_000_000);
    try {
      new Yaml(new SafeConstructor(options)).load(text);
    } catch (MarkedYAMLException e) {
      // Do not include SnakeYAML's source snippet: token configuration may contain credentials.
      var mark = e.getProblemMark();
      throw new InvalidYamlException(mark == null ? null : mark.getLine() + 1,
          mark == null ? null : mark.getColumn() + 1);
    } catch (RuntimeException e) {
      throw new InvalidYamlException(null, null);
    }
  }

  /** Syntax location without embedding the source document in an error or log. */
  public static final class InvalidYamlException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    public final Integer line;
    public final Integer column;

    public InvalidYamlException(Integer line, Integer column) {
      super("Invalid YAML: use one document with unique keys"
          + (line == null ? "" : " (line " + line + ", column " + column + ")"));
      this.line = line;
      this.column = column;
    }
  }
}

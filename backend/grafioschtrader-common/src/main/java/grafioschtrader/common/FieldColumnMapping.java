package grafioschtrader.common;

import java.lang.reflect.Field;

/**
 * Contains the import field row to the target field of history quote.
 */
public class FieldColumnMapping {
  public final int col;
  public final Field field;
  public boolean required;

  public FieldColumnMapping(int col, Field field) {
    this.col = col;
    this.field = field;
  }

  @Override
  public String toString() {
    return "FieldColumnMapping [col=" + col + ", field=" + field + "]";
  }
}

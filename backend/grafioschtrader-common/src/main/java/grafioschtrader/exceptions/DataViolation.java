package grafioschtrader.exceptions;

import java.util.Arrays;

public class DataViolation {

  private final String field;
  private final String messageKey;
  private final Object[] data;
  private final boolean translateFieldName;

  public DataViolation(final String field, final String messageKey, final Object[] data, boolean translateFieldName) {
    this.field = field;
    this.messageKey = messageKey;
    this.data = data;
    this.translateFieldName = translateFieldName;
  }

  /**
   *
   * @param field
   * @param messageKey
   * @param id         Meaningful identification which helps the user by selecting
   *                   the wrong data record.
   */
  public DataViolation(final String field, final String messageKey, final Object singleData,
      boolean translateFieldName) {
    this(field, messageKey, new Object[] { singleData }, translateFieldName);
  }

  public Object[] getData() {
    return data;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public String getField() {
    return field;
  }

  public boolean isTranslateFieldName() {
    return translateFieldName;
  }

  @Override
  public String toString() {
    return "DataViolation [field=" + field + ", messageKey=" + messageKey + ", data=" + Arrays.toString(data)
        + ", translateFieldName=" + translateFieldName + "]";
  }

}

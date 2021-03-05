package grafioschtrader.exceptions;

public class DataViolation {

  String field;
  String messageKey;
  Object[] data;

  public DataViolation(final String field, final String messageKey, final Object[] data) {
    this.field = field;
    this.messageKey = messageKey;
    this.data = data;
  }

  /**
   * 
   * @param field
   * @param messageKey
   * @param id         Meaningful identification which helps the user by selecting
   *                   the wrong data record.
   */
  public DataViolation(final String field, final String messageKey, final Object singleData) {
    this(field, messageKey, new Object[] { singleData });
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

}

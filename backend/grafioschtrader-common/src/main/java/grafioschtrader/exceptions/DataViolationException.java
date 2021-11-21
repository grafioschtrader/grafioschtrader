package grafioschtrader.exceptions;

import java.util.ArrayList;
import java.util.List;

public class DataViolationException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private String localeStr;

  List<DataViolation> dataViolation = new ArrayList<>();

  public DataViolationException() {
  }

  public DataViolationException(final String field, final String messageKey, final Object[] data, String localeStr) {
    this.addDataViolation(field, messageKey, data);
    this.localeStr = localeStr;
  }

  public DataViolationException(final String field, final String messageKey, final Object data, String localeStr) {
    this(field, messageKey, new Object[] { data }, localeStr);
  }

  public DataViolationException(final String field, final String messageKey, final Object[] data) {
    this.addDataViolation(field, messageKey, data);
  }

  public void addDataViolation(final String field, final String messageKey, final Object data,
      boolean translateFieldName) {
    dataViolation.add(new DataViolation(field, messageKey, new Object[] { data }, translateFieldName));
  }

  public void addDataViolation(final String field, final String messageKey, final Object data) {
    dataViolation.add(new DataViolation(field, messageKey, new Object[] { data }, true));
  }

  public void addDataViolation(final String field, final String messageKey, final Object[] data,
      boolean translateFieldName) {
    dataViolation.add(new DataViolation(field, messageKey, data, translateFieldName));
  }

  public void addDataViolation(final String field, final String messageKey, final Object[] data) {
    dataViolation.add(new DataViolation(field, messageKey, data, true));
  }

  public String getLocaleStr() {
    return localeStr;
  }

  public boolean hasErrors() {
    return this.dataViolation.size() > 0;
  }

  public List<DataViolation> getDataViolation() {
    return dataViolation;
  }

  @Override
  public String toString() {
    return "DataViolationException [localeStr=" + localeStr + ", dataViolation=" + dataViolation + "]";
  }

}

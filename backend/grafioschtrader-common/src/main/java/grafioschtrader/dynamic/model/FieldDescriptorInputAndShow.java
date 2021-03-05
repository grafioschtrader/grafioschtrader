package grafioschtrader.dynamic.model;

import java.io.Serializable;

import grafioschtrader.algo.strategy.model.DataTypeJava;

public class FieldDescriptorInputAndShow implements Serializable {

  private static final long serialVersionUID = 1L;

  public String fieldName;

  public DataType dataType;
  public Long min;
  public Long max;
  public boolean required;
  public DynamicFormPropertyHelps[] dynamicFormPropertyHelps;

  public FieldDescriptorInputAndShow(String fieldName, Class<?> type) {
    super();
    this.fieldName = fieldName;
    setJavaTypeToGenericType(type);
  }

  private void setJavaTypeToGenericType(Class<?> type) {
    switch (DataTypeJava.fromClass(type)) {
    case IntegerC:
      dataType = DataType.NumericInteger;
      break;

    case LocalDate:
      dataType = DataType.DateString;
      break;

    case StringC:
      dataType = DataType.String;
      break;

    default:
      dataType = DataType.None;
    }
  }

}

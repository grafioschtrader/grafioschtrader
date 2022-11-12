package grafioschtrader.dynamic.model;

import java.io.Serializable;
import java.util.Arrays;

public class FieldDescriptorInputAndShow implements Serializable {

  private static final long serialVersionUID = 1L;

  public String fieldName;

  public DataType dataType;
  public Long min;
  public Long max;
  public String enumType;
  public boolean required;
  public DynamicFormPropertyHelps[] dynamicFormPropertyHelps;

  public FieldDescriptorInputAndShow(String fieldName, Class<?> type) {
    super();
    this.fieldName = fieldName;
    setJavaTypeToGenericType(type);
  }

  private void setJavaTypeToGenericType(Class<?> type) {
    if (type instanceof Class && ((Class<?>) type).isEnum()) {
      setForEnum(type);
    } else {
      setDataType(type);
    }
  }

  private void setDataType(Class<?> type) {
    switch (DataTypeJava.fromClass(type)) {
    
    case Byte:
    case ByteC:
    case IntegerC:
      dataType = DataType.NumericInteger;
      break;

    case Boolean:
    case BooleanC:
      dataType = DataType.Boolean;
      break;

    case LocalDate:
      dataType = DataType.DateString;
      break;

    case StringC:
      dataType = DataType.String;
      break;

    case LccalDateTime:
      dataType = DataType.DateTimeNumeric;
      break;

    default:
      dataType = DataType.None;
    }
  }

  private void setForEnum(Class<?> model) {
    Arrays.stream(model.getDeclaredFields()).filter(f -> f.getName().equals("value")).findFirst().ifPresent(f -> {
      setDataType(f.getType());
      enumType = model.getSimpleName();
    });
  }

}

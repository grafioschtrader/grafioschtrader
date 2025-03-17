package grafiosch.dynamic.model;

import java.io.Serializable;
import java.util.Arrays;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
This metadata is intended for a generative input form. 
An instance corresponds to an input element. An instance may be created from the analysis of a class or via persisted metadata.
""")
public class FieldDescriptorInputAndShow implements Serializable {

  private static final long serialVersionUID = 1L;

  public String fieldName;

  public DataType dataType;
  public Double min;
  public Double max;
  public String enumType;
  public boolean required;
  public DynamicFormPropertyHelps[] dynamicFormPropertyHelps;

  public FieldDescriptorInputAndShow(String fieldName, DataType dataType, Double min, Double max) {
    super();
    this.fieldName = fieldName;
    this.dataType = dataType;
    this.min = min;
    this.max = max;
  }
  
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
    case Integer:
    case IntegerC:
      dataType = DataType.NumericInteger;
      break;

    case Boolean:
    case BooleanC:
      dataType = DataType.Boolean;
      break;

    case Double:
    case DoubleC:
      dataType = DataType.Numeric;
      break;  
      
    case LocalDate:
      dataType = DataType.DateString;
      break;

    case StringC:
      dataType = DataType.String;
      break;

    case LocalDateTime:
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

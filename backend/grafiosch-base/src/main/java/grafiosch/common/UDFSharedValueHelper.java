package grafiosch.common;

import grafiosch.types.UDFDataType;

/** Converts between the JSON representation of UDF values and shared canonical text. */
public final class UDFSharedValueHelper {
  private UDFSharedValueHelper() {
  }

  public static Object toJsonValue(String fieldValue, UDFDataType type) {
    return type == UDFDataType.UDF_Boolean ? Boolean.valueOf(fieldValue) : fieldValue;
  }

  public static String toCanonical(Object jsonValue) {
    return jsonValue == null || "".equals(jsonValue) ? null : jsonValue.toString();
  }
}

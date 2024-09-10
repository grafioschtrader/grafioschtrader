package grafioschtrader.dynamic.model.udf;

import grafioschtrader.types.UDFDataType;

public abstract class UDFDataHelper {

  /**
   * Determines the maximum value of a decimal number based on the number of
   * digits before and after the decimal point. For example, the value 99.9999 is
   * returned with the parameters for 2 digits before and 4 digits after the
   * decimal point.
   * 
   * @param toalLength
   * @param suffix
   * @return
   */
  public static double getMaxDecimalValue(int toalLength, int suffix) {
    int integerPartLength = toalLength - suffix;
    double maxIntegerPart = Math.pow(10, integerPartLength) - 1;
    double maxFractionalPart = (Math.pow(10, suffix) - 1) / Math.pow(10, suffix);
    return maxIntegerPart + maxFractionalPart;
  }
  
  public static boolean isFieldSizeForDataType(UDFDataType udfType) {
    return udfType == UDFDataType.UDF_Numeric || udfType == UDFDataType.UDF_NumericInteger
        || udfType == UDFDataType.UDF_String;
  }
  
 
}

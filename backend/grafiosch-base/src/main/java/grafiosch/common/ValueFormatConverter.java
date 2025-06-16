package grafiosch.common;

import java.beans.PropertyDescriptor;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.beanutils.PropertyUtils;

import grafiosch.BaseConstants;

/**
 * Utility class for converting string values to appropriate data types and setting them on bean properties. This
 * converter handles locale-specific formatting for numbers, dates, and times, making it suitable for processing user
 * input from different locales and formats.</br>
 * 
 * The converter supports:</br>
 * - Numeric types (Short, Integer, Long, Double) with custom thousand separators and decimal separators</br>
 * - Date types (Date, LocalDate) with configurable date formats</br>
 * - Time types (LocalTime) with configurable time formats</br>
 * - String types with optional empty/zero clearing</br>
 * - Automatic type detection and conversion based on target property types</br>
 * 
 * The class provides multiple constructors to accommodate different formatting requirements, from simple default locale
 * formatting to fully customized separators and date formats. It integrates with Apache Commons BeanUtils for dynamic
 * property setting on arbitrary objects.
 */
public class ValueFormatConverter {
  private NumberFormat numberFormat;
  private SimpleDateFormat simpleDateFormat;
  private DateTimeFormatter localTimeFormater;
  private DateTimeFormatter localDateFormatter;
  private String thousandSeparatorsPattern;
  private Locale userLocale;

  /**
   * Creates a converter with default system locale settings. Uses standard date format and default locale number
   * formatting.
   */
  public ValueFormatConverter() {
    this.numberFormat = NumberFormat.getInstance(Locale.getDefault());
    simpleDateFormat = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
    localDateFormatter = DateTimeFormatter.ofPattern(BaseConstants.STANDARD_DATE_FORMAT);
    this.thousandSeparatorsPattern = Pattern.quote("" + "" + new DecimalFormatSymbols().getDecimalSeparator());
  }

  /**
   * Creates a converter with fully customized formatting options. Allows specification of date formats, time formats,
   * separators, and locale.
   *
   * @param dateFormat                the date format pattern for Date and LocalDate parsing
   * @param localTimeFormat           the time format pattern for LocalTime parsing, null to use legacy Date handling
   * @param thousandSeparators        the character used as thousand separator in numbers
   * @param thousandSeparatorsPattern regex pattern for removing thousand separators
   * @param decimalSeparator          the character used as decimal separator in numbers
   * @param userLocale                the locale for number and date formatting
   */
  public ValueFormatConverter(String dateFormat, String localTimeFormat, char thousandSeparators,
      String thousandSeparatorsPattern, char decimalSeparator, Locale userLocale) {
    if (localTimeFormat == null) {
      simpleDateFormat = new SimpleDateFormat(dateFormat);
    } else {
      localTimeFormater = DateTimeFormatter.ofPattern(localTimeFormat);
      localDateFormatter = DateTimeFormatter.ofPattern(dateFormat);
    }
    this.setSeparators(decimalSeparator, thousandSeparators);
    this.thousandSeparatorsPattern = thousandSeparatorsPattern;
    this.userLocale = userLocale;
  }

  public ValueFormatConverter(char decimalSeparator, String dateFormat, char thousandSeparators) {
    this(decimalSeparator, thousandSeparators);
    simpleDateFormat = userLocale == null ? new SimpleDateFormat(dateFormat)
        : new SimpleDateFormat(dateFormat, userLocale);

  }

  public ValueFormatConverter(char decimalSeparator, char thousandSeparators) {
    setSeparators(decimalSeparator, thousandSeparators);
    this.thousandSeparatorsPattern = Pattern.quote("" + thousandSeparators);
  }

  private void setSeparators(char decimalSeparator, char thousandSeparators) {
    DecimalFormat format = (DecimalFormat) NumberFormat.getInstance();
    DecimalFormatSymbols custom = new DecimalFormatSymbols();
    custom.setDecimalSeparator(decimalSeparator);
    custom.setGroupingSeparator(thousandSeparators);
    format.setDecimalFormatSymbols(custom);
    this.numberFormat = format;
  }

  public void convertAndSetValue(Object bean, String fieldName, String value, Class<?> dataType) throws Exception {
    this.convertAndSetValue(bean, fieldName, value, dataType, false);
  }

  /**
   * Converts a string value to the appropriate data type and sets it on the bean property. This method handles
   * automatic type conversion based on the target data type, supporting numeric types, date/time types, and strings
   * with proper locale-aware formatting.</br>
   * 
   * Supported conversions:</br>
   * - Numeric types: removes thousand separators and parses using configured number format</br>
   * - Date: parses using SimpleDateFormat or converts from timestamp</br>
   * - LocalDate: parses using configured DateTimeFormatter</br>
   * - LocalTime: parses using configured time formatter</br>
   * - String: used as-is</br>
   * 
   * The clearZeroEmpty option allows automatic clearing of zero numeric values and blank strings by setting them to
   * null instead.
   *
   * @param bean           the target object to set the property on
   * @param fieldName      the name of the property to set
   * @param value          the string value to convert and set
   * @param dataType       the target data type for conversion
   * @param clearZeroEmpty if true, converts zero numbers and blank strings to null
   * @throws Exception if conversion fails or property setting encounters errors
   */
  public void convertAndSetValue(Object bean, String fieldName, String value, Class<?> dataType, boolean clearZeroEmpty)
      throws Exception {
    Object convertValue = value;

    if (Short.class == dataType || short.class == dataType) {
      value = cleanThousandSeparator(value);
      convertValue = numberFormat.parse(value).shortValue();
    } else if (Double.class == dataType || double.class == dataType) {
      value = cleanThousandSeparator(value);
      convertValue = numberFormat.parse(value).doubleValue();
    } else if (Integer.class == dataType || int.class == dataType) {
      value = cleanThousandSeparator(value);
      convertValue = numberFormat.parse(value).intValue();
    } else if (Long.class == dataType || long.class == dataType) {
      value = cleanThousandSeparator(value);
      convertValue = numberFormat.parse(value).longValue();
    } else if (Date.class == dataType) {
      if (simpleDateFormat != null) {
        convertValue = simpleDateFormat.parse(value);
      } else {
        convertValue = new Date(numberFormat.parse(value).longValue() * 1000);
      }
    } else if (LocalDate.class == dataType) {
      convertValue = LocalDate.parse(value, localDateFormatter);
    } else if (LocalTime.class == dataType) {
      convertValue = LocalTime.parse(value, localTimeFormater);
    }

    if (clearZeroEmpty
        && (((Double.class == dataType || Integer.class == dataType) && convertValue.toString().equals("0"))
            || (String.class == dataType && ((String) convertValue).isBlank()))) {
      convertValue = null;
    }

    PropertyUtils.setSimpleProperty(bean, fieldName, convertValue);
  }

  private String cleanThousandSeparator(String number) {
    return number.replaceAll(this.thousandSeparatorsPattern, "");
  }

  /**
   * Extracts property names and their data types from a bean object. Uses reflection to inspect all readable properties
   * of the bean and creates a mapping of property names to their Java class types. This information can be used for
   * dynamic type conversion and validation.
   *
   * @param bean the object to inspect for property information
   * @return a map containing property names as keys and their Class types as values
   */
  public static Map<String, Class<?>> getDataTypeOfPropertiesByBean(Object bean) {
    Map<String, Class<?>> propertyDataTypeMap = new HashMap<>();
    PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(bean);
    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
      propertyDataTypeMap.put(propertyDescriptor.getName(), propertyDescriptor.getPropertyType());
    }
    return propertyDataTypeMap;
  }

}

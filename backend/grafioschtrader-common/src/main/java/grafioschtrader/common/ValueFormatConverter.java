package grafioschtrader.common;

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

import grafioschtrader.GlobalConstants;

public class ValueFormatConverter {
  private NumberFormat numberFormat;
  private SimpleDateFormat simpleDateFormat;
  private DateTimeFormatter localTimeFormater;
  private DateTimeFormatter localDateFormatter;
  private String thousandSeparatorsPattern;
  private Locale userLocale;

  public ValueFormatConverter() {
    this.numberFormat = NumberFormat.getInstance(Locale.getDefault());
    simpleDateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    localDateFormatter = DateTimeFormatter.ofPattern(GlobalConstants.STANDARD_DATE_FORMAT);
    this.thousandSeparatorsPattern = Pattern.quote("" + "" + new DecimalFormatSymbols().getDecimalSeparator());
  }

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
   *
   *
   * @param bean      The Objects which property is changed
   * @param fieldName The name of the property
   * @param value
   * @param dataType
   * @throws Exception
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

  public static Map<String, Class<?>> getDataTypeOfPropertiesByBean(Object bean) {
    Map<String, Class<?>> propertyDataTypeMap = new HashMap<>();
    PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(bean);
    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
      propertyDataTypeMap.put(propertyDescriptor.getName(), propertyDescriptor.getPropertyType());
    }
    return propertyDataTypeMap;
  }

}

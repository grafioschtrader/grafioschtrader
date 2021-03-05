package grafioschtrader.dto;

public class SupportedCSVFormat {
  public char decimalSeparator;
  public char thousandSeparator;
  public String dateFormat;

  public SupportedCSVFormat(char decimalSeparator, char thousandSeparator, String dateFormat) {
    this.decimalSeparator = decimalSeparator;
    this.thousandSeparator = thousandSeparator;
    this.dateFormat = dateFormat;
  }

  public static class SupportedCSVFormats {
    public String[] thousandSeparators = new String[] { ".", ",", " ", "'" };
    public String[] dateFormats = new String[] { "dd.MM.yy", "dd.MM.yyyy", "dd/MM/yy", "dd/MM/yyyy", "yy-MM-dd",
        "yyyy-MM-dd" };
    public String[] decimalSeparators = new String[] { ".", "," };
  }

}
package grafioschtrader.search;

import java.io.Serializable;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One criterion of the advanced search on a user-defined field or an indicator. Which attributes are used depends on
 * the value type of the chosen field; the others stay empty.
 */
@Schema(description = """
    A criterion on a user-defined field or an indicator. NUMBER and DATE fields use the operator and the first
    comparison value, and the second one only with BETWEEN. TEXT fields use the text and BOOLEAN fields the flag.""")
public class AdvancedSearchCriterion implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Longest text of a text criterion. */
  public static final int MAX_TEXT_LENGTH = 100;

  @Schema(description = "Key of the field as offered by the field list of the search: 'U' or 'I' and an id.")
  private String field;

  @Schema(description = "Comparison of a NUMBER or DATE field; empty for TEXT and BOOLEAN.")
  private AdvancedSearchOperator operator;

  @Schema(description = "Comparison value of a NUMBER field. With BETWEEN the lower end.")
  private Double number;

  @Schema(description = "Upper end of a NUMBER field with BETWEEN.")
  private Double numberTo;

  @Schema(description = "Comparison value of a DATE field. With BETWEEN the first day. Format: yyyyMMdd")
  @DateTimeFormat(pattern = "yyyyMMdd")
  private LocalDate date;

  @Schema(description = "Last day of a DATE field with BETWEEN. Format: yyyyMMdd")
  @DateTimeFormat(pattern = "yyyyMMdd")
  private LocalDate dateTo;

  @Schema(description = "Part of the text of a TEXT field; case is ignored.")
  private String text;

  @Schema(description = "Wanted value of a BOOLEAN field.")
  private Boolean flag;

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public AdvancedSearchOperator getOperator() {
    return operator;
  }

  public void setOperator(AdvancedSearchOperator operator) {
    this.operator = operator;
  }

  public Double getNumber() {
    return number;
  }

  public void setNumber(Double number) {
    this.number = number;
  }

  public Double getNumberTo() {
    return numberTo;
  }

  public void setNumberTo(Double numberTo) {
    this.numberTo = numberTo;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public LocalDate getDateTo() {
    return dateTo;
  }

  public void setDateTo(LocalDate dateTo) {
    this.dateTo = dateTo;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Boolean getFlag() {
    return flag;
  }

  public void setFlag(Boolean flag) {
    this.flag = flag;
  }
}

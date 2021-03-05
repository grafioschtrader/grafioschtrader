package grafioschtrader.connector.instrument.finanzen;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class HeaderAttribute {
  String attribute;
  String value;
  boolean calculate;

  HeaderAttribute(String attribute) {
    this.attribute = attribute;
  }

  HeaderAttribute(String attribute, boolean calculate) {
    this(attribute);
    this.calculate = calculate;
  }

  void setValue(String value) {
    if (calculate) {
      ExpressionParser parser = new SpelExpressionParser();
      this.value = (parser.parseExpression(value).getValue(Integer.class)).toString();
    } else {
      this.value = value;
    }
  }

  boolean hasValue() {
    return value != null;
  }

  @Override
  public String toString() {
    return "headerAttribute [attribute=" + attribute + ", value=" + value + ", calculate=" + calculate + "]";
  }
}
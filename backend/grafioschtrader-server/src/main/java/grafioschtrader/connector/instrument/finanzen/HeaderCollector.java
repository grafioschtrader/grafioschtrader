package grafioschtrader.connector.instrument.finanzen;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.methods.HttpPost;

public class HeaderCollector {
  HeaderAttribute[] headerAttributes = { new HeaderAttribute("__atts"), new HeaderAttribute("__ath"),
      new HeaderAttribute("__atcrv", true) };
  Pattern p = Pattern.compile("value=\\\"(.+)\\\"");

  boolean checkLineForAttribute(String line) {
    boolean allSet = true;
    for (HeaderAttribute headerAttribute : headerAttributes) {
      if (!headerAttribute.hasValue()) {
        if (line.contains(headerAttribute.attribute)) {
          Matcher m = p.matcher(line);
          if (m.find()) {
            headerAttribute.setValue(m.group(1));
          } else {
            allSet = false;
          }
        } else {
          allSet = false;
        }
      }
    }
    return allSet;
  }

  void addToHeader(HttpPost httpPost) {
    for (HeaderAttribute headerAttribute : headerAttributes) {
      httpPost.addHeader(headerAttribute.attribute, headerAttribute.value);
    }
  }

  @Override
  public String toString() {
    return "HeaderCollector [headerAttributes=" + Arrays.toString(headerAttributes) + "]";
  }

}

package grafioschtrader.connector.instrument.finanzench;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import grafioschtrader.GlobalConstants;

public class FinanzenNl {

  // ETF -> Keine Unterstützung
  // static String url =
  // "https://www.finanzen.nl/aandelen/historisch/ING/ASX/1.1.2018_8.10.2018";
  // static String url2 =
  // "https://www.finanzen.nl/Ajax/SharesController_HistoricPriceList/ING/ASX/1.1.2018_8.10.2018";

  // Stock (ING Group Aandeel)
  static String url = "https://www.finanzen.ch/obligationen/historisch/vat_groupsf-anl_201823-obligation-2023-ch0417086052";
  static String url2 = "https://www.finanzen.ch/Ajax/BondController_HistoricPriceList/vat_groupsf-anl_201823-obligation-2023-ch0417086052/FSE/1.1.2018_10.10.2018";

  // Bond
  // static String url =
  // "https://www.finanzen.nl/obligaties/historisch/Bobst_Group_SASF-Anl_201420-Obligatie-2020-CH0249899110/SWX/1.1.2017_5.10.2018";
  // static String url2 =
  // "https://www.finanzen.nl/Ajax/BondController_HistoricPriceList/Bobst_Group_SASF-Anl_201420-Obligatie-2020-CH0249899110/SWX/1.1.2017_5.10.2018";

  public static void main(String[] args) {

    HeaderCollector headerCollector = new HeaderCollector();
    HttpClientBuilder builder = HttpClientBuilder.create();
    builder.setUserAgent(GlobalConstants.USER_AGENT);
    HttpClient httpClient = builder.build();

    HttpGet httpGet = new HttpGet(url);

    try {
      HttpResponse httpResponse = httpClient.execute(httpGet);
      HttpEntity entity = httpResponse.getEntity();
      if (entity != null) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()))) {
          String inputLine = null;
          boolean allSet = false;
          while ((inputLine = in.readLine()) != null && !allSet) {
            allSet = headerCollector.checkLineForAttribute(inputLine);
          }
        }
      }

    } catch (Throwable error) {
      throw new RuntimeException(error);
    }

    System.out.println(headerCollector);

    HttpPost httpPost = new HttpPost(url2);
    headerCollector.addToHeader(httpPost);
    try {
      HttpResponse httpResponse = httpClient.execute(httpPost);
      HttpEntity entity = httpResponse.getEntity();
      if (entity != null) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()))) {
          String inputLine = null;
          while ((inputLine = in.readLine()) != null) {
            System.out.println(inputLine);
          }
        }
      }

    } catch (Throwable error) {
      throw new RuntimeException(error);
    }

  }

  static class HeaderCollector {
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

  }

  static class HeaderAttribute {
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

}

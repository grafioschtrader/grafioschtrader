package grafioschtrader.connector.instrument.finanzench;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import grafioschtrader.GlobalConstants;

public class FinanzenNet {

  private static String domain = "https://www.finanzen.net/";

  // Stock
  // static String url = "https://www.finanzen.net/historische-kurse/Daimler";

  // Bond
  static String url = "https://www.finanzen.net/anleihen/a19qsv-ff-group-finance-luxembourg-ii-anleihe-historisch";

  public static void main(String[] args) {

    java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(java.util.logging.Level.FINEST);
    java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);

    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
    System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "debug");
    System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "debug");
    System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "debug");

    FinanzenNet finanzenNet = new FinanzenNet();
    try {
      finanzenNet.readData1();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void readData1() throws IOException {
    CookieStore cookieStore = new BasicCookieStore();
    String pkBHTs = readPkBHTs(cookieStore);
    if (pkBHTs != null) {
      collectData(pkBHTs, cookieStore);
    }
  }

  private String readPkBHTs(CookieStore cookieStore) {
    String pkBHTs = null;

    HttpGet httpGet = new HttpGet(domain + "holedaten.asp?strFrag=vdBHTSService");
    try (CloseableHttpClient httpclient = HttpClients.createDefault();
        CloseableHttpResponse httpResponse = httpclient.execute(httpGet)) {
      HttpEntity entity = httpResponse.getEntity();
      pkBHTs = EntityUtils.toString(entity);
      addToCookieStore(httpResponse, cookieStore);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return pkBHTs;
  }

  private void collectData(String pkBHTs, CookieStore cookieStore) throws IOException {

    Response response = Jsoup.connect(url).userAgent(GlobalConstants.USER_AGENT).timeout(10 * 1000).method(Method.POST)
        .header("Cookie", getCookiesAsString(cookieStore)).data("inTag1", "1").data("inMonat1", "1")
        .data("inJahr1", "2018").data("inTag2", "7").data("inMonat2", "10").data("inJahr2", "2018")
        .data("stBoerse", "FSE").data("pkBHTs", pkBHTs).execute();
    Document doc = response.parse();
    Elements elements = doc.getElementsByClass("box table-quotes");
    Element table = elements.get(0).select("table").get(0);
    Elements rows = table.select("tr");

    for (int i = 1; i < rows.size(); i++) { // first row is the col names so skip it.
      Element row = rows.get(i);
      Elements cols = row.select("td");
      System.out.println(cols);
    }
  }

  public void readData() {
    CookieStore cookieStore = new BasicCookieStore();
    HttpClientBuilder builder = HttpClientBuilder.create();
    builder.setUserAgent(GlobalConstants.USER_AGENT);
    HttpClient httpClient = builder.build();
    HttpClientContext context = HttpClientContext.create();
    context.setCookieStore(cookieStore);

    // HttpGet httpRequest = new HttpGet(url);

    HttpGet pkBHTsHttpRequest = new HttpGet("https://www.finanzen.net/holedaten.asp?strFrag=vdBHTSService");

    HttpResponse httpResponse = null;
    try {
      // 1
      // ##########################
      // httpResponse = httpClient.execute(httpRequest, context);
      // showCookieStore(1, cookieStore);
      // addCookies(httpResponse, cookieStore);
      // showCookieStore(1, cookieStore);
      // showHeader(httpResponse);
      // 2
      // ##########################
      httpResponse = httpClient.execute(pkBHTsHttpRequest, context);
      showCookieStore(2, cookieStore);
      addToCookieStore(httpResponse, cookieStore);
      addOmappvp(cookieStore);
      showCookieStore(2, cookieStore);
      showHeader(httpResponse);
      HttpEntity entity = httpResponse.getEntity();
      String pkBHTs = EntityUtils.toString(entity);

      // 3
      // ##########################
      HttpPost httpPost = new HttpPost(url);
      HttpContext localContext = new BasicHttpContext();
      System.out.println("----");
      System.out.println(cookieStore.toString());
      System.out.println("----");
      localContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

      // httpPost.setEntity(new UrlEncodedFormEntity(setRequestParams(pkBHTs),
      // "UTF-8"));
      httpPost.setEntity(new UrlEncodedFormEntity(setRequestParams(pkBHTs)));
      httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");

      httpPost.setHeader("Cookie", getCookiesAsString(cookieStore));

      HttpResponse response = httpClient.execute(httpPost);

      // showCookieStore(3, cookieStore);
      // System.out.println("Status-code:" +
      // response.getStatusLine().getStatusCode());
      entity = response.getEntity();
      System.out.println(EntityUtils.toString(entity));

    } catch (Throwable error) {
      throw new RuntimeException(error);
    }

    /* check cookies */

    // System.out.println(httpCookieStore);
  }

  private void showCookieStore(int requestNumber, CookieStore cookieStore) {
    System.out.println("Request: " + requestNumber + " Number of Cookies:" + cookieStore.getCookies().size());
    for (Cookie cookie : cookieStore.getCookies()) {
      System.out.println("Name:" + cookie.getName() + " value:" + cookie.getValue());
    }
  }

  public String getCookiesAsString(CookieStore cookieStore) {
    String cookiesString = "";
    for (Cookie cookie : cookieStore.getCookies()) {
      cookiesString += cookie.getName() + "=" + cookie.getValue() + "; ";
    }
    return cookiesString;
  }

  private void addToCookieStore(HttpResponse httpResponse, CookieStore cookieStore) {
    Header[] headers = httpResponse.getHeaders("Set-Cookie");

    if (headers != null) {
      // cookieStore.clear();
      for (Header header : headers) {

        String cookie = header.getValue();
        String[] cookievalues = cookie.split(";");

        for (String cookievalue : cookievalues) {
          String[] keyPair = cookievalue.split("=", 2);
          String key = keyPair[0].trim();

          String value = keyPair.length > 1 ? keyPair[1].trim() : "";
          BasicClientCookie newCookie = new BasicClientCookie(key, value);
          cookieStore.addCookie(newCookie);
        }
      }
    }
  }

  public void addOmappvp(CookieStore cookieStore) {
    String e = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    String omappvp = "";
    for (int n = 96; 0 < n; --n) {
      omappvp += e.charAt((int) Math.floor(Math.random() * e.length()));
    }

    BasicClientCookie newCookie = new BasicClientCookie("_omappvp", omappvp);
    cookieStore.addCookie(newCookie);
    newCookie = new BasicClientCookie("fnb-template-popup-integration", "hide=2");
    cookieStore.addCookie(newCookie);

  }

  public void addCookie(HttpResponse httpResponse, CookieStore cookieStore) {
    List<Header> httpHeaders = Arrays.asList(httpResponse.getAllHeaders());
    for (Header header : httpHeaders) {
      if (header.getName().equals("Set-Cookie") && header.getName().equals("fintargeting")) {
        // Cookie cookie = new BasicClientCookie();
        // cookieStore.addCookie(cookie);
        System.out.println(header.getName() + "=" + header.getValue());
      }
    }
  }

  public void showHeader(HttpResponse httpResponse) {
    List<Header> httpHeaders = Arrays.asList(httpResponse.getAllHeaders());
    for (Header header : httpHeaders) {
      if (header.getName().equals("Set-Cookie")) {
        System.out.println(header.getName() + "=" + header.getValue());
      }
    }
  }

  public ArrayList<NameValuePair> setRequestParams(String pkBHTs) {
    ArrayList<NameValuePair> postParameters = new ArrayList<>();
    postParameters.add(new BasicNameValuePair("inTag1", "1"));
    postParameters.add(new BasicNameValuePair("inMonat1", "1"));
    postParameters.add(new BasicNameValuePair("inJahr1", "2018"));
    postParameters.add(new BasicNameValuePair("inTag2", "1"));
    postParameters.add(new BasicNameValuePair("inMonat2", "10"));
    postParameters.add(new BasicNameValuePair("inJahr2", "2018"));
    postParameters.add(new BasicNameValuePair("stBoerse", "SWX"));
    postParameters.add(new BasicNameValuePair("pkBHTs", pkBHTs));
    return postParameters;
  }

}

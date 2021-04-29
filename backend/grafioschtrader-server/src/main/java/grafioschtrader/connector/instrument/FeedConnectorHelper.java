package grafioschtrader.connector.instrument;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import grafioschtrader.entities.Historyquote;

public class FeedConnectorHelper {

  public static Double parseDoubleGE(String item) {
    final String text = item.replace(".", "").replace(",", ".");
    return text.trim().length() > 0 ? Double.parseDouble(text) : null;
  }

  public static Long parseLongGE(String item) {
    final String text = item.replace(".", "");
    return text.trim().length() > 0 ? Long.parseLong(text) : null;
  }

  public static Historyquote parseResponseLineGE(final String inputLine, final Date from, final Date to,
      final SimpleDateFormat dateFormat) throws ParseException {
    Historyquote historyquote = null;
    final String[] item = inputLine.split(";", -1);

    Date readDate = dateFormat.parse(item[0]);
    if (readDate.getTime() >= from.getTime() && readDate.getTime() <= to.getTime()) {
      historyquote = new Historyquote();
      historyquote.setDate(readDate);
      historyquote.setOpen(FeedConnectorHelper.parseDoubleGE(item[1]));
      historyquote.setHigh(FeedConnectorHelper.parseDoubleGE(item[2]));
      historyquote.setLow(FeedConnectorHelper.parseDoubleGE(item[3]));
      historyquote.setClose(FeedConnectorHelper.parseDoubleGE(item[4]));

      historyquote.setVolume(FeedConnectorHelper.parseLongGE(item[5]));
    }
    return historyquote;
  }

}

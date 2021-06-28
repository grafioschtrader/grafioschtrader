package grafioschtrader.connector.instrument;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Historyquote;

public class FeedConnectorHelper {

  private static final Logger log = LoggerFactory.getLogger(FeedConnectorHelper.class);

  public static Double parseDoubleGE(String item) {
    final String text = item.replace(".", "").replace(",", ".");
    return text.trim().length() > 0 ? Double.parseDouble(text) : null;
  }

  public static Double parseDoubleUS(String item) {
    final String text = item.replace(",", "");
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

  public static List<Historyquote> checkFirstLastHistoryquoteAndRemoveWhenOutsideDateRange(Date fromDate, Date toDate,
      List<Historyquote> historyquotes, String instrumentName) {
    for (int i = 0; !historyquotes.isEmpty() && i < historyquotes.size(); i += historyquotes.size() - 1) {
      Historyquote historyquote = historyquotes.get(i);
      var fromDateCheck = DateHelper.setTimeToZeroAndAddDay(fromDate, 0);
      if (historyquote.getDate().before(fromDateCheck) || historyquote.getDate().after(toDate)) {
        log.warn("Removed historyquote with date {} from instrument {}. Date range was {}-{}", historyquote.getDate(),
            instrumentName, fromDate, toDate);
        historyquotes.remove(i);
      }
    }
    return historyquotes;

  }

}

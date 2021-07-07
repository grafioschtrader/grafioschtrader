package grafioschtrader.connector.instrument.finanzen;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;

public abstract class FinanzenBase<T extends Securitycurrency<T>> {

  protected final IFeedConnector feedConnector;
  protected String domain;
  private NumberFormat numberFormat;

  public abstract List<Historyquote> getHistoryquotes(final T security, final Date from, final Date to,
      int[] headerColMapping) throws Exception;

  public List<Historyquote> getHistoryquotes(final T security, final Date from, final Date to) throws Exception {
    return getHistoryquotes(security, from, to, null);
  }

  public FinanzenBase(String domain, IFeedConnector feedConnector, Locale locale) {
    this.domain = domain;
    this.feedConnector = feedConnector;
    numberFormat = NumberFormat.getNumberInstance(locale);
  }

  /**
   *
   * @param table
   * @param historyquotes
   * @param headerColMapping Header in the first row is expected when
   *                         headerColMapping is not set
   * @return Number of rows in the table (includes the header)
   * @throws ParseException
   */
  protected int parseTableContent(final Element table, final List<Historyquote> historyquotes, int[] headerColMapping)
      throws ParseException {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy"); //$NON-NLS-1$
    Elements rows = table.select("tr");

    int[] colMapping = (headerColMapping == null) ? new int[] { 0, 1, 2, 3, 4, 5 } : headerColMapping;
    for (int i = 0; i < rows.size(); i++) {
      Element row = rows.get(i);

      if (i == 0 && headerColMapping == null) {
        // Header in the first row is expected when headerColMapping is not set
        Elements cols = row.select("th");
        colMapping = createMapping(cols);
      } else {

        Elements cols = row.select("td");
        String date = cols.get(0).text();
        if (!date.equals("00:00")) {
          final Historyquote historyquote = new Historyquote();

          historyquote.setDate(dateFormat.parse(date));

          if (colMapping[1] != -1) {
            final Double open = parseDouble(colMapping[1], cols);
            if (open != null) {
              historyquote.setOpen(open);
            }
          }

          final Double close = parseDouble(colMapping[2], cols);
          if (close != null) {
            historyquote.setClose(close);
          } else {
            continue;
          }

          if (colMapping[3] != -1) {
            final Double high = parseDouble(colMapping[3], cols);
            if (high != null) {
              historyquote.setHigh(high);
            }
          }

          if (colMapping[4] != -1) {
            final Double low = parseDouble(colMapping[4], cols);
            if (low != null) {
              historyquote.setLow(low);
            }
          }

          if (colMapping[5] != -1) {
            final Long volume = parseLong(colMapping[5], cols);
            historyquote.setVolume(volume == null ? null : volume);
          }

          historyquotes.add(historyquote);
        }
      }
    }
    return rows.size();
  }

  private Double parseDouble(final int column, final Elements cols) {
    Number number = parseNumber(column, cols);
    return number == null ? null : number.doubleValue();
  }

  private Long parseLong(final int column, final Elements cols) {
    Number number = parseNumber(column, cols);
    return number == null ? null : number.longValue();
  }

  private Number parseNumber(final int column, final Elements cols) {
    String text = cols.get(column).text().trim().replace("'", "");
    if (!text.isEmpty() && !text.equals("-")) {
      try {
        return numberFormat.parse(text);
      } catch (ParseException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  private int[] createMapping(Elements headerCols) {
    int[] mapping = new int[] { -1, -1, -1, -1, -1, -1 };

    for (int i = 0; i < headerCols.size(); i++) {
      String text = headerCols.get(i).text();
      if (text.contains(" ")) {
        text = text.split(" ")[0];
      }
      switch (text) {

      case "Datum":
        mapping[0] = i;
        break;
      case "Eröffnung":
        mapping[1] = i;
        break;
      case "Schluss":
      case "Schlusskurs":
        mapping[2] = i;
        break;
      case "Tageshoch":
        mapping[3] = i;
        break;
      case "Tagestief":
        mapping[4] = i;
        break;
      case "Volumen":
      case "Umsatz":
        mapping[5] = i;
        break;
      }
    }
    return mapping;
  }

}

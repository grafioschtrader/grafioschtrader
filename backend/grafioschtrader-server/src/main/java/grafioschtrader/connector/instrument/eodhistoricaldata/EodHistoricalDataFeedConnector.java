package grafioschtrader.connector.instrument.eodhistoricaldata;

import java.util.Map;

import grafioschtrader.connector.instrument.BaseFeedConnector;

public class EodHistoricalDataFeedConnector extends BaseFeedConnector {

  public EodHistoricalDataFeedConnector(Map<FeedSupport, FeedIdentifier[]> supportedFeed, String id,
      String readableNameKey) {
   
    super(supportedFeed, id, readableNameKey);
   
  }

}

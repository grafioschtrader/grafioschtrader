### Different Behavior of Open Server and Push-Open Server
The behavior of these two types of sharing intraday price data differs fundamentally. To enable active exchange of useful price data, instances should preferably focus on push-open servers. The open server concept is more intended for special cases. This price exchange should not lead to poor management of the connectors for individual instruments.
#### Intraday Price Data Exchange with Push-Open Server
The instance can also be configured as push-open for intraday prices. These are contacted first by priority before instances configured as "normal" open. With these instances, there is only bidirectional price data exchange, regardless of the settings in "Exchange Price Data Securities".
- For a push-open server to reach its full potential, it should certainly be contacted by 20 instances regularly, i.e., several times daily. There should be few push-open servers, but they should demonstrate a certain level of performance to keep response times short.
- The push-open server stores intraday price data in separate structures in persistence. Thus, it is independent of the instruments on the corresponding server. It is quite conceivable that not a single instrument has been recorded on this server.
- In Grafioschtrader, securities are recognized as unique based on their ISIN and currency code.
- For currency pairs, it is the combination of base currency and quote currency.
##### Push-Open Server to Push-Open Server
This is the most complex use case because ultimately the own push-open server is also updated. It can use its own push-open server price data and does not need to connect with other servers.
Here is the sequence of data exchange:
1. Form the intersection of securities and currency pairs from a watchlist with the securities and currency pairs released for exchange in the "Exchange of Securities" configuration.
2. Send a request to your own push-open server using the identified instruments.
3. If all prices come from your own push-offline server, the price query for the day is over. If not, continue with the request to the push-open server.
- 3.1 The price request can be answered by your own server.
- 3.2 Not all prices are available
    1. Continue with *General Flow with Request to Push-Open Server*.
    2. Update the price data of your own push-open server.
##### General Flow with Request to Push-Open Server
1. Determine all push-open instances according to their priorities with which your own instance is in exchange. If there are multiple instances with the same priority, an instance is randomly selected.
2. Send the above-mentioned intersection with intraday price data and the timestamp of the last update to the remote server.
3. The remote server's response contains intraday price data that is more current than that contained in the request.
4. The local securities and currency pairs receive intraday price data updates from this response.
5. If a second configured push-open instance is available, it is now queried for intraday price data. Otherwise, a remote instance configured as "open" for exchange is selected according to priority.
6. The request to the second instance differs only in that the transmitted price data may be more current.
7. The local securities and currency pairs receive intraday price data updates from this response.
8. For securities and currency pairs that have not yet received an update, the configured connector of the instrument's intraday data source is used.
9. The updated prices from the second remote server and the current prices received via the connector are sent to the first remote server. It responds with the number of updated price data it received through the transmission.
10. Only the current prices coming through the connector go to the second remote server. It responds with the number of updated price data it received through the transmission.
### Open Server to Open Server
In this case, probably only a small intraday price data exchange will take place, as there are fewer participating instances. It makes little sense to operate only data exchange with the open server.
- In contrast to the push-open instance, no parallel structure of price data is maintained here. The update is made directly on the intraday price data of the instruments.
- This allows for asymmetric exchange of intraday price data, i.e., the request transmits a last timestamp of null, while the response for this instrument contains a price. This is due to the "Exchange Price Data Securities" setting.
1. Determine all open instances according to their priorities with which your own instance is in exchange. If there are multiple instances with the same priority, an instance is randomly selected.
2. Send the above-mentioned intersection with intraday price data and the timestamp of the last update to the open remote server.
3. The remote server's response contains intraday price data that is more current than that contained in the request. Additionally, the response can also contain prices from instruments that had a timestamp of null in the request.
4. The local securities and currency pairs receive intraday price data updates from this response.
5. If a second configured open instance is available, it is now queried for intraday price data.
6. The request to the second instance differs only in that the price data transmitted to this remote server may be more current.
7. The local securities and currency pairs receive intraday price data updates from this response.
8. For securities and currency pairs that have not yet received an update, the configured connector of the instrument's intraday data source is used.
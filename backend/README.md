## Build backend
### Data Provider for Investment data
GT obtains price, dividend and split data from various sources. For some of them it needs an API key, but only **CryptoCompare** is required if you want to use crypto currencies. Please get the following:  
- [Alpha Vantage](//www.alphavantage.co/): The free API Key has some data access limits. 5 API requests per minute and 500 API requests per day. Take look at [Alpha Vantage Premium API Key](//www.alphavantage.co/premium/)
- [The Free Currency Converter API](//free.currencyconverterapi.com/): Please note the limits of "[Free Version](//free.currencyconverterapi.com)".
- [Finnhub Stock API](//finnhub.io/): I made the experience that the access of the free API key is more and more restricted. Otherwise, the Finnhub compared to other providers very expensive and therefore less and less atractive.
- [CryptoCompare API](//min-api.cryptocompare.com/): This is the standard data provider for cryptocurrency. If a currency pair is required, GT creates it automatically. In a such case the standard data provider is involved.
- [EOD Historical Data](//eodhistoricaldata.com/): GT uses the services of the subscription "[EOD Historical Data - All World](//eodhistoricaldata.com/pricing)". The free package does not make sense for GT.
- [StockData.org](https://www.stockdata.org/): GT uses the services of the [paid subscriptions](//eodhistoricaldata.com/pricing). The free package does not make sense for GT.  

### GT dependencies
GT depends heavily on other libraries, to get them. Execute the following:
```
# In directory backend
mvn clean install -Dmaven.test.skip=true
```
### application.properties
GT has some properties in the configuration file **backend/grafioschtrader-server/src/main/resources/application.properties**. The properties which settings starts with *ENC* must have a new value and other properties should be checked:
- spring.datasource.url
- spring.datasource.username
- spring.datasource.password
- gt.eod.cron.quotation
- gt.main.user.admin.mail
- gt.allowed.users
- spring.mail.*
- app.jwt.secret
- gt.connector.*

Some propertis are encrypted with **Jasypt**. Those properties values starts with "ENC(" replace it with your secrect value like "spring.datasource.password = DEC(YOUR_DB_PASSWORD)" and when your propties are all set, execute the following:

```
# In directory backend/grafioschtrader-server
mvn jasypt:encrypt -Djasypt.encryptor.password="YOUR_Jasypt_PASSWORD"
```
All properties values with "DEC(...)" are now encrypted with "ENC(...). **If you follow the installation path of Wiki, then go back to the main path.**

### Build and execute without scripts
GT provides some shell scripts which make the following manual creation of the backend unnecessary. We recommend you to use these shell scripts.
#### Build executable backend
Everytime the **application.properties** are changed the executable must be rebuild:
```
# In directory backend
mvn package -Dmaven.test.skip=true
```
#### Start the backend
Since we use **Jasypt**, the enviroment variable **JASYPT_ENCRYPTOR_PASSWORD** must be set before the backend of GT can launched properly. The first time the start of GT may take longer since the database is initialized.
```
# On Windows SET JASYPT_ENCRYPTOR_PASSWORD=YOUR_Jasypt_PASSWORD 
# On Linux export JASYPT_ENCRYPTOR_PASSWORD=YOUR_Jasypt_PASSWORD
# In directory backend
java -jar ./grafioschtrader-server/target/grafioschtrader-server-0.XX.X.jar
```
### Optimize mariadb
MariaDB deserves a lot of memory resources to operate GT well. Please adjust the following system variables of InnoDB to your system needs. The following settings are rather minimal:
```
innodb_buffer_pool_size=1GB
tmp_table_size=128MB
```

### When Flyway first time initialization fails
If the settings for the database were incorrect and the database needs to be reinitialized. You can execute the following statement:
```
mysql -u root -p -D grafioschtrader -e "DROP DATABASE grafioschtrader"
```
Afterwards recreate the database again.
## Build deployment artifacts
The deployment artifact is ready when you follow the steps for building the executable backend. 

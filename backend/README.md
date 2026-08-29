## Build backend

### GT dependencies
GT depends heavily on other libraries, to get them. Execute the following:
```
# In directory backend
mvn clean install -Dmaven.test.skip=true
```
### Property files
There are three [property](./grafioschtrader-server/src/main/resources) files in the backend:
- **application.yaml**: The properties are set by the software developer. This property file is always overwritten during the update.
- **application.properties**: The values of this property file should be adjusted according to the configuration of your environment. Your settings will be kept during GT updates if you use the shell scripts provided by us. However, updates will delete the properties that are not present in the application.properties source. And non-existing properties are added by the source. Therefore, only values of the properties should be changed.
- **application-production.properties**: Here you can make your own settings. These properties remain unaffected by an update. GT delivers an empty file here. A value in this property file overrides the value in the other two property files. Therefore, certain properties should only be overwritten with enough basic knowledge.

#### application.properties
GT has some properties in the configuration file `backend/grafioschtrader-server/src/main/resources/application.properties`. **Properties whose value begins with *ENC* must be given a new value and then begin with *DEC*. The following properties should be checked and possibly adjusted**:
- spring.datasource.url
- spring.datasource.username
- spring.datasource.password
- gt.eod.cron.quotation
- gt.dividend.update.data
- g.main.user.admin.mail
- g.allowed.users
- spring.mail.*
- g.jwt.secret

Some propertis are encrypted with **Jasypt**. Those properties values starts with "ENC(" replace it with your secrect value like "spring.datasource.password = DEC(YOUR_DB_PASSWORD)" and when your propties are all set, execute the following:

```
# In directory backend/grafioschtrader-server
mvn jasypt:encrypt -Djasypt.encryptor.password="YOUR_Jasypt_PASSWORD"
```
All properties values with "DEC(...)" are now encrypted with "ENC(...)".  **If you follow the installation path of Wiki, then go back to the main path.**

#### Times of the daily data download
Grafioschtrader loads its end-of-day prices with `gt.eod.cron.quotation` and its dividend data with
`gt.dividend.update.data`. Both jobs fetch from **free, public data providers** (Yahoo, Finnhub,
Boursorama and others), and every installation is delivered with the *same* default times. If nobody
changes them, all Grafioschtrader instances world-wide query those providers within the same minute.
That produces an entirely avoidable load peak on servers that are made available to us free of
charge, and it risks throttling or blocking - for every GT user, not only for the one causing it.
**Please therefore give your installation its own times.**

A good window is **05:00 to 08:00 local time**: the stock exchanges are closed and the day has not
yet begun.

**All cron expressions are evaluated in UTC**, whatever the time zone of your server is. When you set
a value by hand, convert your local time to UTC first - on a server in Central European Summer Time,
06:20 local is `0 20 04 * * ?`.

**Move the whole morning chain, not one job alone.** Three further jobs depend on the two above and
run after them, so all five belong together:

| Property | Purpose |
|---|---|
| `gt.eod.cron.quotation` | loads the end-of-day prices |
| `gt.dividend.update.data` | loads the dividends |
| `gt.standing.order.execution` | executes the standing orders, needs the closing prices of the price run |
| `gt.check.inactive.dividend` | checks for inactive instruments and missing dividends |
| `gt.hold.consistency.check` | compares the `hold_*` tables against the transactions and reports drift |

If you move only `gt.eod.cron.quotation`, the standing orders may be executed before the new prices
have arrived and would then use the closing prices of the previous day. Shift all five by the same
amount so that their order and their spacing are preserved.

**This normally happens automatically.** On the first build after an installation, `gtupbackend.sh`
runs `util/shellscripts/gtcronrandom.sh`, which draws one random slot between 05:00 and 07:00 local
time and moves the whole chain there - but only while all five properties are still at their
delivered values. As soon as one of them differs, the script reports this and never touches your
schedule again. Set the environment variable `GT_CRON_RANDOMIZE=off` to switch the mechanism off, or
run the script yourself:

```
# In the root directory of the repository
bash util/shellscripts/gtcronrandom.sh --file backend/grafioschtrader-server/src/main/resources/application.properties --dry-run
```

Two things to keep in mind:
- The property files are packaged into the executable JAR, so **every change of a time requires a
  rebuild** (`gtupbackend.sh`, or `mvn package` as described below).
- The stored value is UTC and therefore fixed. In a time zone with daylight saving time the job
  consequently runs one hour earlier or later in local time for one half of the year.

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

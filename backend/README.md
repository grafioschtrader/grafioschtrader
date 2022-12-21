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
GT has some properties in the configuration file **backend/grafioschtrader-server/src/main/resources/application.properties**. The properties which settings starts with *ENC* must have a new value and other properties should be checked:
- spring.datasource.url
- spring.datasource.username
- spring.datasource.password
- gt.eod.cron.quotation
- gt.main.user.admin.mail
- gt.allowed.users
- spring.mail.*
- app.jwt.secret

Some propertis are encrypted with **Jasypt**. Those properties values starts with "ENC(" replace it with your secrect value like "spring.datasource.password = DEC(YOUR_DB_PASSWORD)" and when your propties are all set, execute the following:

```
# In directory backend/grafioschtrader-server
mvn jasypt:encrypt -Djasypt.encryptor.password="YOUR_Jasypt_PASSWORD"
```
All properties values with "DEC(...)" are now encrypted with "ENC(...).  **If you follow the installation path of Wiki, then go back to the main path.**

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

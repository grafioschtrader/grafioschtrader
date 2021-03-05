# Grafioschtrader (GT)
![Architektur](manual/content/Komponenten.svg)

## What's all about
* Take a look at the [manual](//www.grafioschtrader.info/manual/de/intro/) in German which is in progress.
* Take a look at [GT](//www.grafioschtrader.info/grafioschtrader) with E-Mail "gt1@grafioschtrader.info" and Passwort "gt1" or create your own account.

## Development Environment
### Prerequisite
* [Java JDK 11](https://jdk.java.net/java-se-ri/11): The Java development Kit for the backend
* [Apache Maven](https://maven.apache.org/): The build system for the backend
* [Node.js with npm](https://nodejs.org/en/): The build system for frontend
* [MariaDB](https://mariadb.org/): All data are saved in an instance of MariaDB Server
* [HUGO](https://gohugo.io/): It a static site generator. It is used for help pages. The Web user interface has links to this weg pages.
### Email account
GT requires access to an Email account for user registration. The settings for Google's Gmail would be similar to the following. Note that for Gmail maybe the security settings must changed. Gmail must be enabled for less secure apps.
```
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=grafiosch@gmail.com
spring.mail.password=DEC(YOUR_PASSWORD)
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```
### Database preparation
For the database only a empty database with user access is required. Everthing else is done with the first start of GT. In the following statement change at least **YOUR_PASSWORD" with your own password.
```
mysql -u root -p -e "create database grafioschtrader; GRANT ALL PRIVILEGES ON grafioschtrader.* TO grafioschtrader@localhost IDENTIFIED BY 'YOUR_PASSWORD'"
```
## Buid development enviroment
1. Install the required Software
2. [Build and run the backend](backend/README.md)
3. [Build and run the frontend](frontend/README.md)
4. [Optional build and run the manual](manual/README.md)
5. [Optional build the transform application](transform/README.md)

## Deployment
### Prerequisite for target machine
* Java JRE 11
* MariaDB
* Apache HTTP Server

```
# First time install 
npm install -g @angular/cli
# In directory frontend 
# The --base-href --deploy-url it not needed when everything is deployed in a document root
ng build --prod --base-href /grafioschtrader/ --deploy-url /grafioschtrader/
```
Transfer the files including the asset folder from **frontend/dist** to the target directory of your Webserver.
For Apache place the following .htaccess in the directory of your deplyment directory:
```
RewriteEngine On
# If an existing asset or directory is requested go to it as it is
RewriteCond %{DOCUMENT_ROOT}%{REQUEST_URI} -f [OR]
RewriteCond %{DOCUMENT_ROOT}%{REQUEST_URI} -d
RewriteRule ^ - [L]

# If the requested resource doesn't exist, use index.html
RewriteRule ^ /index.html
```

## Install and run frontend
```
# Directory frontend
npm install
npm start
```
## Build deployment artifacts
For deployment the [Angular CLI](//cli.angular.io/) is required
```
# Directory frontend
npm install -g @angular/cli
ng build --prod --base-href /grafioschtrader/ --deploy-url /grafioschtrader/
```

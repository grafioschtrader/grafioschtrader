import {Component, OnInit} from '@angular/core';
import {RouterModule} from '@angular/router';

import {SharedTabMenuComponent} from '../../lib/tabmenu/component/shared.tab.menu.component';
import {TabItem} from '../../lib/types/tab.item';
import {AppSettings} from '../../shared/app.settings';

/**
 * Tab menu component for GTNet Exchange Log.
 * Provides navigation between LAST_PRICE and HISTORICAL_PRICES logs.
 */
@Component({
  selector: 'gtnet-exchange-log-tabmenu',
  standalone: true,
  imports: [
    SharedTabMenuComponent,
    RouterModule
  ],
  template: `
    <app-shared-tab-menu
      [tabs]="tabs"
      [defaultRoute]="defaultRoute">
      <router-outlet></router-outlet>
    </app-shared-tab-menu>
  `
})
export class GTNetExchangeLogTabMenuComponent implements OnInit {

  tabs: TabItem[] = [];
  defaultRoute: string = AppSettings.GT_NET_EXCHANGE_LOG_LASTPRICE_KEY;

  ngOnInit(): void {
    this.initializeTabs();
  }

  private initializeTabs(): void {
    const tabsConfig: [string, string][] = [
      ['LAST_PRICE', AppSettings.GT_NET_EXCHANGE_LOG_LASTPRICE_KEY],
      ['HISTORICAL_PRICES', AppSettings.GT_NET_EXCHANGE_LOG_HISTORICAL_KEY]
    ];

    this.tabs = tabsConfig.map(([label, route]) => ({
      label,
      route,
      icon: ''
    }));
  }
}

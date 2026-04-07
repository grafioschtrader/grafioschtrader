import {Component, OnInit} from '@angular/core';
import {RouterModule} from '@angular/router';

import {SharedTabMenuComponent} from '../../tabmenu/component/shared.tab.menu.component';
import {TabItem} from '../../types/tab.item';
import {BaseSettings} from '../../base.settings';

/**
 * Tab menu component for GTNet Exchange Log.
 * Provides navigation between LAST_PRICE, HISTORICAL_PRICES, and SECURITY_METADATA logs.
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
  defaultRoute: string = BaseSettings.GT_NET_EXCHANGE_LOG_LASTPRICE_KEY;

  ngOnInit(): void {
    this.initializeTabs();
  }

  private initializeTabs(): void {
    const tabsConfig: [string, string][] = [
      ['LAST_PRICE', BaseSettings.GT_NET_EXCHANGE_LOG_LASTPRICE_KEY],
      ['HISTORICAL_PRICES', BaseSettings.GT_NET_EXCHANGE_LOG_HISTORICAL_KEY],
      ['SECURITY_METADATA', BaseSettings.GT_NET_EXCHANGE_LOG_METADATA_KEY]
    ];

    this.tabs = tabsConfig.map(([label, route]) => ({
      label,
      route,
      icon: ''
    }));
  }
}

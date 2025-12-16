import {Component, OnInit} from '@angular/core';
import {RouterModule} from '@angular/router';

import {SharedTabMenuComponent} from '../../lib/tabmenu/component/shared.tab.menu.component';
import {TabItem} from '../../lib/types/tab.item';
import {AppSettings} from '../../shared/app.settings';

/**
 * Tab menu component for GTNetExchange configuration.
 * Provides navigation between securities and currency pairs configuration tabs.
 */
@Component({
  selector: 'gtnet-exchange-tabmenu',
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
export class GTNetExchangeTabMenuComponent implements OnInit {

  tabs: TabItem[] = [];
  defaultRoute: string = AppSettings.GT_NET_EXCHANGE_SECURITIES_KEY;

  ngOnInit(): void {
    this.initializeTabs();
  }

  private initializeTabs(): void {
    const tabsConfig: [string, string][] = [
      ['SECURITY', AppSettings.GT_NET_EXCHANGE_SECURITIES_KEY],
      ['CURRENCYPAIR', AppSettings.GT_NET_EXCHANGE_CURRENCYPAIRS_KEY]
    ];

    this.tabs = tabsConfig.map(([label, route]) => ({
      label,
      route,
      icon: ''
    }));
  }
}

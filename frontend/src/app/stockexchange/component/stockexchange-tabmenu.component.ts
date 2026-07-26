import {Component, OnInit} from '@angular/core';
import {RouterModule} from '@angular/router';

import {SharedTabMenuComponent} from '../../lib/tabmenu/component/shared.tab.menu.component';
import {TabItem} from '../../lib/types/tab.item';
import {AppSettings} from '../../shared/app.settings';

/**
 * Tab menu for the stock exchange base data. It groups the exchanges themselves with the trading calendar rule sets,
 * because a rule set is one of the two sources from which an exchange derives its non trading days.
 */
@Component({
  selector: 'stockexchange-tabmenu',
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
export class StockexchangeTabMenuComponent implements OnInit {

  tabs: TabItem[] = [];
  defaultRoute: string = AppSettings.STOCKEXCHANGE_TAB_EXCHANGES_KEY;

  ngOnInit(): void {
    this.initializeTabs();
  }

  private initializeTabs(): void {
    const tabsConfig: [string, string][] = [
      ['STOCKEXCHANGE', AppSettings.STOCKEXCHANGE_TAB_EXCHANGES_KEY],
      ['TRADING_CALENDAR_RULE_SET', AppSettings.STOCKEXCHANGE_TAB_RULE_SETS_KEY]
    ];

    this.tabs = tabsConfig.map(([label, route]) => ({
      label,
      route,
      icon: ''
    }));
  }
}

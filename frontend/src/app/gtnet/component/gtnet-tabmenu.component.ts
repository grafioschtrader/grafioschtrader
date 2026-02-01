import {Component, OnInit} from '@angular/core';
import {RouterModule} from '@angular/router';

import {SharedTabMenuComponent} from '../../lib/tabmenu/component/shared.tab.menu.component';
import {TabItem} from '../../lib/types/tab.item';
import {AppSettings} from '../../shared/app.settings';

/**
 * Tab menu component for GTNet functionality.
 * Provides navigation between GTNet Setup and Admin Messages tabs.
 */
@Component({
  selector: 'gtnet-tabmenu',
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
export class GTNetTabMenuComponent implements OnInit {

  tabs: TabItem[] = [];
  defaultRoute: string = AppSettings.GT_NET_SETUP_KEY;

  ngOnInit(): void {
    this.initializeTabs();
  }

  private initializeTabs(): void {
    const tabsConfig: [string, string][] = [
      ['GT_NET', AppSettings.GT_NET_SETUP_KEY],
      ['GT_NET_ADMIN_MESSAGES', AppSettings.GT_NET_ADMIN_MESSAGES_KEY]
    ];

    this.tabs = tabsConfig.map(([label, route]) => ({
      label,
      route,
      icon: ''
    }));
  }
}

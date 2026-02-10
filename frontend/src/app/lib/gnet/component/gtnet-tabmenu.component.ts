import {Component, OnInit} from '@angular/core';
import {RouterModule} from '@angular/router';

import {SharedTabMenuComponent} from '../../tabmenu/component/shared.tab.menu.component';
import {TabItem} from '../../types/tab.item';
import {BaseSettings} from '../../base.settings';

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
  defaultRoute: string = BaseSettings.GT_NET_SETUP_KEY;

  ngOnInit(): void {
    this.initializeTabs();
  }

  private initializeTabs(): void {
    const tabsConfig: [string, string][] = [
      ['GT_NET', BaseSettings.GT_NET_SETUP_KEY],
      ['GT_NET_ADMIN_MESSAGES', BaseSettings.GT_NET_ADMIN_MESSAGES_KEY]
    ];

    this.tabs = tabsConfig.map(([label, route]) => ({
      label,
      route,
      icon: ''
    }));
  }
}

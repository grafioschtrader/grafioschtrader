import { Component, OnInit } from '@angular/core';
import { AppSettings } from '../../../shared/app.settings';
import { GlobalSessionNames } from '../../../shared/global.session.names';
import { GlobalparameterService } from '../../../shared/service/globalparameter.service';
import {TabItem} from '../../../shared/types/tab.item';



@Component({
  template: `
    <app-shared-tab-menu
      [tabs]="tabs"
      [defaultRoute]="defaultRoute"
      [useSessionStorage]="true"
      [sessionStorageKey]="sessionStorageKey">
      <router-outlet></router-outlet>
    </app-shared-tab-menu>
  `,
  standalone: false
})
export class TenantTabMenuComponent implements OnInit {
  tabs: TabItem[] = [];
  defaultRoute: string = AppSettings.PORTFOLIO_KEY;
  sessionStorageKey: string = GlobalSessionNames.TAB_MENU_TENANT;

  constructor(private gps: GlobalparameterService) {}

  ngOnInit(): void {
    this.initializeTabs();
  }

  /**
   * Initialize tabs with conditional logic for alert functionality
   */
  private initializeTabs(): void {
    // Base tab configuration
    const baseTabsConfig: [string, string][] = [
      ['PORTFOLIOS', AppSettings.PORTFOLIO_KEY],
      ['PEROIDPERFORMANCE', AppSettings.PERFORMANCE_TAB_KEY],
      ['SECURITYACCOUNTS', AppSettings.DEPOT_KEY],
      ['SECURITY_ASSETCLASS_WITH_CASH', AppSettings.DEPOT_CASH_KEY],
      ['PORTFOLIO_DIVIDENDS', AppSettings.DIVIDENDS_ROUTER_KEY],
      ['TRANSACTION_COST', AppSettings.TRANSACTION_COST_KEY],
      ['TRANSACTIONS', AppSettings.TENANT_TRANSACTION],
    ];

    // Add conditional alert tab
    if (this.gps.useAlert()) {
      baseTabsConfig.push(['ALERT', AppSettings.TENANT_ALERT]);
    }

    // Convert to TabItem array
    this.tabs = baseTabsConfig.map(([label, route]) => ({
      label,
      route,
      icon: ''
    }));
  }
}


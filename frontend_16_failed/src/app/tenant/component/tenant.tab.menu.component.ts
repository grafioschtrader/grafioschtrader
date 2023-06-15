import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {MenuItem} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../shared/app.settings';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {GlobalSessionNames} from '../../shared/global.session.names';


@Component({
  template: `
    <p-tabMenu [model]="items" [activeItem]="items[idActiveItem]"></p-tabMenu>
    <router-outlet></router-outlet>
  `
})
export class TenantTabMenuComponent implements OnInit {

  items: MenuItem[] = [];
  idActiveItem = 0;
  nameRouteMapping: string[][] = [
    ['PORTFOLIOS', AppSettings.PORTFOLIO_KEY],
    ['PEROIDPERFORMANCE', AppSettings.PERFORMANCE_TAB_KEY],
    ['SECURITYACCOUNTS', AppSettings.DEPOT_KEY],
    ['SECURITY_ASSETCLASS_WITH_CASH', AppSettings.DEPOT_CASH_KEY],
    ['PORTFOLIO_DIVIDENDS', AppSettings.DIVIDENDS_ROUTER_KEY],
    ['TRANSACTION_COST', AppSettings.TRANSACTION_COST_KEY],
    ['TRANSACTIONS', AppSettings.TENANT_TRANSACTION]
  ];

  constructor(private router: Router, private activatedRoute: ActivatedRoute, public translateService: TranslateService) {
    for (let i = 0; i < this.nameRouteMapping.length; i++) {
      this.items.push({
        label: this.nameRouteMapping[i][0],
        command: (event) => this.navigateTo(i)
      });
    }
    TranslateHelper.translateMenuItems(this.items, this.translateService);
  }

  ngOnInit(): void {
    const activeItem = sessionStorage.getItem(GlobalSessionNames.TAB_MENU_TENANT);
    this.idActiveItem = activeItem ? +activeItem : 0;
    this.navigateTo(this.idActiveItem);
  }

  navigateTo(toIdActiveItem: number): void {
    this.idActiveItem = toIdActiveItem;
    sessionStorage.setItem(GlobalSessionNames.TAB_MENU_TENANT, this.idActiveItem.toString());
    this.router.navigate([this.nameRouteMapping[this.idActiveItem][1]], {relativeTo: this.activatedRoute});
  }

}


import {Component, OnDestroy, OnInit} from '@angular/core';
import {MenuItem} from 'primeng/api';
import {AppSettings} from '../../shared/app.settings';
import {TranslateService} from '@ngx-translate/core';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {Subscription} from 'rxjs';
import {Portfolio} from '../../entities/portfolio';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {GlobalSessionNames} from '../../shared/global.session.names';

@Component({
  template: `
      <p-tabMenu [model]="items" [activeItem]="items[idActiveItem]"></p-tabMenu>
      <router-outlet></router-outlet>
  `
})
export class PortfolioTabMenuComponent implements OnInit, OnDestroy {
  items: MenuItem[] = [];
  idActiveItem = 0;
  nameRouteMapping: string[][] = [
    [AppSettings.PORTFOLIO.toUpperCase(), AppSettings.PORTFOLIO_SUMMARY_KEY],
    ['PEROIDPERFORMANCE', AppSettings.PERFORMANCE_KEY],
    ['TRANSACTIONS', AppSettings.PORTFOLIO_TRANSACTION_KEY]
  ];
  private routeSubscribe: Subscription;
  private portfolio: Portfolio;

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
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      const activeItem = sessionStorage.getItem(GlobalSessionNames.TAB_MENU_PORTFOLIO);
      this.idActiveItem = activeItem ? +activeItem : 0;
      this.portfolio = JSON.parse(params['object']);
      this.navigateTo(this.idActiveItem);
    });

  }

  ngOnDestroy(): void {
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  /**
   * Is needed for the default navigation
   */
  private navigateTo(toIdActiveItem: number): void {
    this.idActiveItem = toIdActiveItem;
    sessionStorage.setItem(GlobalSessionNames.TAB_MENU_PORTFOLIO, this.idActiveItem.toString());
    this.router.navigate([this.nameRouteMapping[this.idActiveItem][1], this.portfolio.idPortfolio,
      {portfolio: JSON.stringify(this.portfolio)}], {relativeTo: this.activatedRoute});
  }
}

import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../shared/app.settings';
import {Subscription} from 'rxjs';
import {Watchlist} from '../../entities/watchlist';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {MenuItem} from 'primeng/api';

@Component({
  template: `
    <p-tabMenu [model]="items" [activeItem]="activeTab"></p-tabMenu>
     <router-outlet></router-outlet>
   `
})
export class WatchlistTabMenuComponent implements OnInit, OnDestroy {
  items: MenuItem[];

  activeTab: MenuItem;
  private routeSubscribe: Subscription;
  private watchlist: Watchlist;

  private lastRouteKey = AppSettings.WATCHLIST_PERFORMANCE_KEY;
  private lastItemIndex = 0;

  constructor(private router: Router, private activatedRoute: ActivatedRoute, public translateService: TranslateService) {
    this.items = [
      {
        label: 'WATCHLIST_PERFORMANCE',
        command: (event) => this.navigateToRoute(AppSettings.WATCHLIST_PERFORMANCE_KEY, 0)
      },
      {
        label: 'WACHTLIST_PRICE_FEED',
        command: (event) => this.navigateToRoute(AppSettings.WATCHLIST_PRICE_FEED_KEY, 1)
      },
      {
        label: 'WACHTLIST_DIVIDEND_SPLIT_FEED',
        command: (event) => this.navigateToRoute(AppSettings.WATCHLIST_DIVIDEND_SPLIT_FEED_KEY, 2)
      }
    ];
    TranslateHelper.translateMenuItems(this.items, this.translateService);
  }

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.watchlist = JSON.parse(params['object']);
      this.navigateToRoute(this.lastRouteKey, this.lastItemIndex);
      this.activeTab = this.items[this.lastItemIndex];
    });
  }

  ngOnDestroy(): void {
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  /**
   * Is needed for the default navigation
   */
  private navigateToRoute(routeKey: string, itemIndex?: number) {
    this.lastRouteKey = routeKey;
    this.lastItemIndex = itemIndex;

    this.router.navigate([routeKey, this.watchlist.idWatchlist, {watchlist: JSON.stringify(this.watchlist)}], {
      relativeTo: this.activatedRoute
    });

  }

}

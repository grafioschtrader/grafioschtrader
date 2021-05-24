import {Component, OnInit} from '@angular/core';
import {MenuItem} from 'primeng/api';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../shared/app.settings';
import {TranslateHelper} from '../../shared/helper/translate.helper';

/**
 * Showing the tab menu with period performance and missing EOD data
 */
@Component({
  template: `
    <p-tabMenu [model]="items" [activeItem]="items[0]"></p-tabMenu>
    <router-outlet></router-outlet>
  `
})
export class TenantPerformanceTabMenuComponent implements OnInit {
  items: MenuItem[];

  constructor(private router: Router, private activatedRoute: ActivatedRoute, public translateService: TranslateService) {
    this.items = [
      {
        label: 'PERFORMANCE',
        command: (event) => this.router.navigate([AppSettings.PERFORMANCE_KEY], {relativeTo: this.activatedRoute})
      },
      {
        label: 'PERFORMANCE_EOD_MISSING',
        command: (event) => this.router.navigate([AppSettings.EOD_DATA_QUALITY_KEY], {relativeTo: this.activatedRoute})
      }
    ];
    TranslateHelper.translateMenuItems(this.items, this.translateService);
  }

  ngOnInit(): void {
    this.navigateToPortfolio();
  }

  private navigateToPortfolio(): void {
    this.router.navigate([AppSettings.PERFORMANCE_KEY], {relativeTo: this.activatedRoute});
  }

}

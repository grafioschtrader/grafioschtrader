import {Component, OnInit, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Params, Router, RouterModule} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {TabsModule} from 'primeng/tabs';

import {AppSettings} from '../../shared/app.settings';
import {Securityaccount} from '../../entities/securityaccount';
import {TabItem} from '../../lib/types/tab.item';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';

/**
 * Tab menu component for security account import sub-tabs.
 * Contains Import Transaction tab and conditionally GTNet Security Import tab (only when GTNet is enabled).
 * Tabs can only be changed manually by user click.
 */
@Component({
  template: `
    <div class="card">
      <p-tabs [value]="activeIndex">
        <p-tablist>
          @for (tab of tabs; track tab.route; let i = $index) {
            <p-tab
              [value]="i"
              (click)="onTabClick(i)">
              <span>{{ tab.label | translate }}</span>
            </p-tab>
          }
        </p-tablist>
      </p-tabs>
      <router-outlet></router-outlet>
    </div>
  `,
  standalone: true,
  imports: [CommonModule, RouterModule, TabsModule, TranslateModule]
})
export class SecurityaccountImportTabMenuComponent implements OnInit, OnDestroy {
  tabs: TabItem[] = [];
  activeIndex: number = 0;

  private securityaccount: Securityaccount;
  private successFailedImportTransParam: any = null;
  private routeParamSubscribe: Subscription;
  private initialized = false;

  constructor(
    private router: Router,
    private activatedRoute: ActivatedRoute,
    private translateService: TranslateService,
    private gps: GlobalparameterService
  ) {
  }

  ngOnInit(): void {
    this.tabs = [
      {
        label: 'IMPORT_TRANSACTION',
        route: AppSettings.SECURITYACCOUNT_IMPORT_KEY,
        icon: ''
      }
    ];

    if (this.gps.useGtnet()) {
      this.tabs.push({
        label: 'GTNET_SECURITY_IMPORT',
        route: AppSettings.SECURITYACCOUNT_GTNET_IMPORT_KEY,
        icon: ''
      });
    }

    TranslateHelper.translateMenuItems(this.tabs, this.translateService);

    this.routeParamSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      if (params[AppSettings.SECURITYACCOUNT.toLowerCase()]) {
        this.securityaccount = JSON.parse(params[AppSettings.SECURITYACCOUNT.toLowerCase()]);

        if (params[AppSettings.SUCCESS_FAILED_IMP_TRANS]) {
          this.successFailedImportTransParam = params[AppSettings.SUCCESS_FAILED_IMP_TRANS];
        }

        // Only navigate to default on first initialization
        if (!this.initialized) {
          this.initialized = true;
          this.navigateToTab(0);
        }
      }
    });
  }

  ngOnDestroy(): void {
    if (this.routeParamSubscribe) {
      this.routeParamSubscribe.unsubscribe();
    }
  }

  onTabClick(index: number): void {
    if (this.activeIndex !== index) {
      this.navigateToTab(index);
    }
  }

  private navigateToTab(index: number): void {
    if (!this.securityaccount) {
      return;
    }

    this.activeIndex = index;
    const tab = this.tabs[index];

    const data: any = {
      securityaccount: JSON.stringify(this.securityaccount)
    };

    // Only pass successFailedImportTransParam to the import transaction tab
    if (index === 0 && this.successFailedImportTransParam) {
      data[AppSettings.SUCCESS_FAILED_IMP_TRANS] = this.successFailedImportTransParam;
      this.successFailedImportTransParam = null;  // Clear after use
    }

    this.router.navigate(
      [tab.route, this.securityaccount.idSecuritycashAccount, data],
      {relativeTo: this.activatedRoute}
    );
  }
}

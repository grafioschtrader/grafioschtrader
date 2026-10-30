import { Component, OnInit, signal, inject, effect, input, numberAttribute, untracked } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TabsModule } from '@openng/optimus-ui/tabs';
import { AppSettings } from '../../shared/app.settings';
import { Securityaccount } from '../../entities/securityaccount';
import { TabItem } from '../../lib/types/tab.item';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';

/**
 * Tab menu component for security account import sub-tabs.
 * Contains Import Transaction tab and conditionally GTNet Security Import tab (only when GTNet is enabled).
 * Tabs can only be changed manually by user click.
 */
@Component({
  template: `
    <div class="card">
      <p-tabs [value]="activeIndex()">
        <p-tablist>
          @for (tab of tabs; track tab.route; let i = $index) {
            <p-tab [value]="i" (click)="onTabClick(i)">
              <span>{{ tab.label | translate }}</span>
            </p-tab>
          }
        </p-tablist>
      </p-tabs>
      <router-outlet />
    </div>
  `,
  imports: [RouterModule, TabsModule, TranslateModule]
})
export class SecurityaccountImportTabMenuComponent implements OnInit {
  readonly importResult = input<string>(undefined, { alias: 'successFailedDirectImpTran' });

  readonly id = input.required({ transform: numberAttribute });

  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);
  private gps = inject(GlobalparameterService);
  private treeNavState = inject(TreeNavigationStateService);

  tabs: TabItem[] = [];
  readonly activeIndex = signal(0);

  private securityaccount: Securityaccount | null;
  private successFailedImportTransParam: any = null;
  private initialized = false;
  constructor() {
    effect(() => {
      const id = this.id();
      const importResult = this.importResult();
      untracked(() => {
        if (id) {
          this.securityaccount = this.treeNavState.getEntity<Securityaccount>(
            AppSettings.SECURITYACCOUNT_IMPORT_TAB_MENU_KEY,
            id
          );

          if (this.securityaccount) {
            if (importResult) {
              this.successFailedImportTransParam = importResult;
            }

            // Only navigate to default on first initialization
            if (!this.initialized) {
              this.initialized = true;
              this.navigateToTab(0);
            }
          }
        }
      });
    });
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
  }

  onTabClick(index: number): void {
    if (this.activeIndex() !== index) {
      this.navigateToTab(index);
    }
  }

  private navigateToTab(index: number): void {
    if (this.securityaccount?.idSecuritycashAccount == null) {
      return;
    }

    this.activeIndex.set(index);
    const tab = this.tabs[index];
    // Store entity in shared service so child components can access it
    this.treeNavState.setEntity(tab.route, this.securityaccount.idSecuritycashAccount, this.securityaccount);
    // Only non-entity params in the URL
    const data: any = {};
    if (index === 0 && this.successFailedImportTransParam) {
      data[AppSettings.SUCCESS_FAILED_IMP_TRANS] = this.successFailedImportTransParam;
      this.successFailedImportTransParam = null;
    }

    this.router.navigate([tab.route, this.securityaccount.idSecuritycashAccount, data], {
      relativeTo: this.activatedRoute
    });
  }
}

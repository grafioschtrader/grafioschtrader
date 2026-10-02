import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { MenuItem } from '@openng/optimus-ui/api';
import { TranslateService } from '@ngx-translate/core';
import { finalize, forkJoin, of } from 'rxjs';
import { BaseSettings } from '../../lib/base.settings';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { TenantService } from '../../tenant/service/tenant.service';
import { PerformanceReportDialogComponent } from '../component/performance-report-dialog.component';
import { PerformanceReportScope } from '../model/performance-report';
import { HoldingService } from './holding.service';

/**
 * Single entry to the PDF report dialog: the period performance passes its displayed period, the tenant and portfolio
 * holdings pass their reporting date, including cash-only portfolios.
 */
@Injectable({ providedIn: 'root' })
export class PerformanceReportDialogService {
  private holding = inject(HoldingService);
  private tenant = inject(TenantService);
  private portfolio = inject(PortfolioService);
  private gps = inject(GlobalparameterService);
  private dialogs = inject(DialogService);
  private translate = inject(TranslateService);

  /** True while the dialog metadata is loading; a second request in that time is ignored. */
  opening = false;

  /**
   * Menu entry of a holdings view for a statement at the view's reporting date.
   *
   * @param date - Reporting date of the view; today when the view has none
   * @param idPortfolio - Portfolio of the view, or null for the whole tenant
   * @param destroyRef - Lifetime of the calling view, which cancels a pending metadata request
   * @returns The menu item opening the dialog
   */
  menu(date: () => Date, idPortfolio: () => number | null, destroyRef: DestroyRef): MenuItem {
    return {
      label: this.translate.instant('PDF_REPORT') + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => {
        const until = date() || new Date();
        const reportDate = `${until.getFullYear()}-${String(until.getMonth() + 1).padStart(2, '0')}-${String(until.getDate()).padStart(2, '0')}`;
        this.open({ reportDate, idPortfolio: idPortfolio() }, destroyRef);
      }
    };
  }

  /**
   * Loads options, remembered settings, scope name and form definition before mounting the dynamic form.
   *
   * @param window - Either the period of a performance calculation or a reporting date, with the optional portfolio
   * @param destroyRef - Lifetime of the calling view, which cancels a pending metadata request
   */
  open(window: PerformanceReportScope, destroyRef: DestroyRef): void {
    if (this.opening) {
      return;
    }
    this.opening = true;
    forkJoin({
      options: this.holding.getReportOptions(),
      settings: this.tenant.getReportSettings(),
      tenant: this.tenant.getTenantAndPortfolio(),
      // Tenant deliberately omits its portfolio collection from JSON. Resolve a portfolio through its own API.
      portfolio: window.idPortfolio ? this.portfolio.getPortfolioByIdPortfolio(window.idPortfolio) : of(null),
      formDefinition: this.gps.getEntityFormDefinition('PerformanceReportRequest')
    })
      .pipe(
        takeUntilDestroyed(destroyRef),
        finalize(() => (this.opening = false))
      )
      .subscribe({
        next: ({ options, settings, tenant, portfolio, formDefinition }) => {
          this.dialogs.open(PerformanceReportDialogComponent, {
            header: this.translate.instant('PDF_REPORT'),
            modal: true,
            closable: true,
            closeOnEscape: true,
            width: 'min(850px, 95vw)',
            data: {
              options,
              settings,
              formDefinition,
              window: { ...window },
              scope: tenant.tenantName + (portfolio ? ' / ' + portfolio.name : ''),
              currency: portfolio?.currency || tenant.currency
            }
          });
        },
        // The services already display the translated HTTP error. Finalize permits another attempt.
        error: () => {}
      });
  }
}

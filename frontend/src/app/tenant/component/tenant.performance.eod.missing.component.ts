import { computed, signal, Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import moment from 'moment';
import { MenuItem } from '@openng/optimus-ui/api';
import { ButtonModule } from '@openng/optimus-ui/button';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { PanelModule } from '@openng/optimus-ui/panel';
import { SelectModule } from '@openng/optimus-ui/select';
import { Subscription } from 'rxjs';
import { Historyquote } from '../../entities/historyquote';
import { Security } from '../../entities/security';
import { TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { BaseSettings } from '../../lib/base.settings';
import { FullyearcalendarLibComponent } from '../../lib/fullyearcalendar/fullyearcalendar-lib.component';
import { RangeSelectDays } from '../../lib/fullyearcalendar/Interface/range.select.days';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HelpIds } from '../../lib/help/help.ids';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { ProcessedAction } from '../../lib/types/processed.action';
import { HoldingService } from '../../performanceperiod/service/holding.service';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { CalendarNavigation } from '../../tradingcalendar/component/calendar.navigation';
import { MissingQuotesWithSecurities } from '../model/missing.quotes.with.securities';
import { TenantPerformanceEodMissingTableComponent } from './tenant-performance-eod-missing-table.component';

/**
 * Displays an annual calendar with a table of missing EOD courses.
 */
@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      (contextmenu)="onRightClick($event)"
      #cmDiv
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <p-panel>
        <p-header>
          <h5>{{ 'MISSING_DAY_CALENDAR_MARK' | translate }}</h5>
        </p-header>
        <label class="small-padding control-label" for="idYearSelect">{{ 'YEAR' | translate }}</label>
        <p-select
          id="idYearSelect"
          [options]="possibleYears()"
          [ngModel]="selectedYear()"
          (onChange)="yearChanged($event)" />
        <ng-fullyearcalendar-lib [locale]="locale" [underline]="underline()" [yearCalendarData]="yearCalendarData()" />
        <p-footer>
          <div class="ui-dialog-buttonpane ui-widget-content flexRight">
            @if (containsYear(yearCalendarData().year - 1)) {
              <p-button class="btn" (click)="addMinusYear(-1)">
                <i class="pi pi-angle-left mr-2"></i>
                {{ yearCalendarData().year - 1 }}
              </p-button>
            }
            @if (containsYear(yearCalendarData().year + 1)) {
              <p-button class="btn" (click)="addMinusYear(1)">
                {{ yearCalendarData().year + 1 }}
                <i class="pi pi-angle-right ml-2"></i>
              </p-button>
            }
          </div>
        </p-footer>
      </p-panel>
      <tenant-performance-eod-missing-table
        [securities]="securities()"
        [selectedDayIdSecurities]="selectedDayIdSecurities()"
        [countIdSecurityMissingsMap]="
          $safeNavigationMigration(missingQuotesWithSecurities()?.countIdSecurityMissingsMap)
        "
        (changedSecurity)="handleChangedSecurity($event)" />

      @if (contextMenuItems().length) {
        <p-contextMenu #contextMenu [model]="contextMenuItems()" [target]="cmDiv" appendTo="body" />
      }
    </div>
  `,
  imports: [
    FormsModule,
    TranslateModule,
    PanelModule,
    SelectModule,
    ButtonModule,
    ContextMenuModule,
    FullyearcalendarLibComponent,
    TenantPerformanceEodMissingTableComponent
  ]
})
export class TenantPerformanceEodMissingComponent extends CalendarNavigation implements IGlobalMenuAttach, OnInit {
  readonly missingQuotesWithSecurities = signal<MissingQuotesWithSecurities>(null);
  readonly securities = computed(() => {
    const report = this.missingQuotesWithSecurities();
    return [
      ...(report?.securities ?? []),
      ...(report?.currencypairs ?? []).map((cp) => ({ ...cp, currency: cp.toCurrency }) as unknown as Security)
    ];
  });
  allMissingDays: Date[] = [];
  securityMissingDays: Date[] = [];
  selectedDayByUser: Date;
  readonly selectedDayIdSecurities = signal<number[]>([]);
  foundDayMarkedRange: boolean;
  selectedSecurity: Security;

  private readDataSub?: Subscription;

  constructor(
    private timeSeriesQuotesService: TimeSeriesQuotesService,
    private holdingService: HoldingService,
    private messageToastService: MessageToastService,
    private router: Router,
    private dataChangedService: DataChangedService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    activePanelService: ActivePanelService
  ) {
    super(translateService, gps, activePanelService, ['yellow']);
  }

  ngOnInit(): void {
    this.dataChangedService.dateChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((processedActionData) => {
      if (processedActionData.data instanceof Historyquote && processedActionData.action === ProcessedAction.UPDATED) {
        this.readData(true);
      }
    });
    this.readData(false);
  }

  readData(yearChange: boolean): void {
    this.readDataSub?.unsubscribe();
    this.readDataSub = this.holdingService
      .getMissingQuotesWithSecurities(this.yearCalendarData().year)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((mqws: MissingQuotesWithSecurities) => {
        this.clearDaySelection(this.selectedDayByUser);
        this.missingQuotesWithSecurities.set(mqws);
        // Securities and currency pairs share one table, because a day without an exchange rate is as unusable for the
        // performance report as a day without a security quote. The pair carries no 'currency' of its own, so the
        // quote currency stands in for it and keeps the price chart of the context menu working.
        this.allMissingDays = Object.keys(mqws.dateSecurityMissingMap).map((day) => new Date(day));
        this.markGroundDays(this.allMissingDays);
        if (!yearChange) {
          const currentYear = moment().year();
          // A tenant without holdings has no first trading day, but still displays the current year's calendar.
          const firstYear = mqws.firstEverTradingDay ? moment(mqws.firstEverTradingDay).year() : currentYear;
          this.setYearsBoundaries(firstYear, currentYear);
        }
      });
  }

  markGroundDays(days: Date[]): void {
    const dates = days.map((day, i): RangeSelectDays => {
      const date = this.getZeroTimeDate(day);
      return {
        id: i,
        start: date,
        end: date,
        color: 'red',
        select: (range: RangeSelectDays, ranges: RangeSelectDays[]) => this.onDayPlusSelect(range, ranges)
      };
    });
    this.yearCalendarData.update((data) => ({ ...data, dates }));
  }

  override getEditMenu(): MenuItem[] {
    return null;
  }

  /**
   * Callback to unmark a day.
   */
  onRangeSelect(range: RangeSelectDays, ranges: RangeSelectDays[]): void {
    !this.foundDayMarkedRange && this.clearDaySelection(this.selectedDayByUser);
    this.foundDayMarkedRange = false;
  }

  onDayPlusSelect(range: RangeSelectDays, ranges: RangeSelectDays[]) {
    this.foundDayMarkedRange =
      ranges.length === 2 && this.securityMissingDays.some((date) => date.getTime() === range.start.getTime());
    this.clearCalendarFromTableSelection();
    if (ranges.length === 1 || this.foundDayMarkedRange) {
      if (this.selectedDayByUser) {
        // Remove existing marked day
        this.addRemoveOnOffDay(this.selectedDayByUser);
      }
      this.addRemoveOnOffDay(range.start);
      this.selectedDayByUser = range.start;
      this.selectedDayIdSecurities.set(
        this.missingQuotesWithSecurities().dateSecurityMissingMap[
          moment(range.start).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE)
        ] ?? []
      );
    }
  }

  handleChangedSecurity(security: Security): void {
    this.selectedSecurity = security;
    this.resetMenu();
    this.clearDaySelection(this.selectedDayByUser);
    this.clearCalendarFromTableSelection();
    if (security) {
      Object.entries(this.missingQuotesWithSecurities().dateSecurityMissingMap).forEach(([date, idsSecurity]) => {
        if (idsSecurity.indexOf(security.idSecuritycurrency) >= 0) {
          const tradingDay = new Date(date);
          tradingDay.setHours(0, 0, 0, 0);
          this.securityMissingDays.push(tradingDay);
        }
      });
    }
    this.addRemoveDays(this.securityMissingDays);
  }

  clearCalendarFromTableSelection(): void {
    this.addRemoveDays(this.securityMissingDays);
    this.securityMissingDays = [];
  }

  getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_PERIODPERFORMANCE;
  }

  protected override getMenuShowOptions(): MenuItem[] {
    let menuItems: MenuItem[] = [];
    if (this.selectedSecurity) {
      menuItems = menuItems.concat(
        this.timeSeriesQuotesService.getMenuItems(
          this.selectedSecurity.idSecuritycurrency,
          this.selectedSecurity.currency,
          false
        )
      );
      menuItems.push(...BusinessHelper.getUrlLinkMenus(this.selectedSecurity));
    }
    return super.getMenuShowOptions().concat(menuItems);
  }

  protected override getContextMenuItems(showMenu: MenuItem[], editMenu: MenuItem[]): MenuItem[] {
    return showMenu.slice(1);
  }

  private clearDaySelection(day: Date): void {
    if (this.selectedDayByUser) {
      this.selectedDayByUser = null;
      this.selectedDayIdSecurities.set([]);
      this.addRemoveOnOffDay(day);
    }
  }
}

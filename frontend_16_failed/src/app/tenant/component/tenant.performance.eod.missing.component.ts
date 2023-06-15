import {Component, OnDestroy, OnInit} from '@angular/core';
import {CalendarNavigation} from '../../tradingcalendar/component/calendar.navigation';
import {RangeSelectDays} from '../../fullyearcalendar/Interface/range.select.days';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {MenuItem} from 'primeng/api';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../shared/help/help.ids';
import {HoldingService} from '../../shared/performanceperiod/service/holding.service';
import {MissingQuotesWithSecurities} from '../model/missing.quotes.with.securities';
import {Security} from '../../entities/security';
import * as moment from 'moment';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {Subscription} from 'rxjs';
import {Router} from '@angular/router';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ProcessedAction} from '../../shared/types/processed.action';
import {Historyquote} from '../../entities/historyquote';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {AppSettings} from '../../shared/app.settings';

/**
 * Displays an annual calendar with a table of missing EOD courses.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" (contextmenu)="onRightClick($event)"
         #cmDiv [ngClass]=" {'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-panel>
        <p-header>
          <h5>{{'MISSING_DAY_CALENDAR_MARK'|translate}}</h5>
        </p-header>
        <label class="small-padding control-label" for="idYearSelect">{{'YEAR' | translate}}</label>
        <p-dropdown id="idYearSelect" [options]="possibleYears" [(ngModel)]="selectedYear"
                    (onChange)="yearChanged($event)">
        </p-dropdown>
        <ng-fullyearcalendar-lib [locale]="locale" [underline]="underline" [yearCalendarData]="yearCalendarData">
        </ng-fullyearcalendar-lib>
        <p-footer>
          <div class="ui-dialog-buttonpane ui-widget-content flexRight">
            <button pButton class="btn" (click)="addMinusYear(-1)" label="{{yearCalendarData.year-1}}"
                    icon="pi pi-angle-left" *ngIf="containsYear(yearCalendarData.year - 1)">
            </button>
            <button pButton class="btn" (click)="addMinusYear(1)" label="{{yearCalendarData.year+1}}" iconPos="right"
                    icon="pi pi-angle-right" *ngIf="containsYear(yearCalendarData.year + 1)">
            </button>

          </div>
        </p-footer>
      </p-panel>
      <tenant-performance-eod-missing-table
        [securities]="securities"
        [selectedDayIdSecurities]="selectedDayIdSecurities"
        [countIdSecurityMissingsMap]="missingQuotesWithSecurities?.countIdSecurityMissingsMap"
        (changedSecurity)="handleChangedSecurity($event)">
      </tenant-performance-eod-missing-table>

      <p-contextMenu *ngIf="contextMenuItems" #contextMenu [model]="contextMenuItems" [target]="cmDiv" appendTo="body">
      </p-contextMenu>
    </div>
  `
})
export class TenantPerformanceEodMissingComponent extends CalendarNavigation implements IGlobalMenuAttach, OnInit, OnDestroy {
  missingQuotesWithSecurities: MissingQuotesWithSecurities;
  securities: Security[] = [];
  allMissingDays: Date[] = [];
  securityMissingDays: Date[] = [];
  selectedDayByUser: Date;
  selectedDayIdSecurities: number[] = [];
  foundDayMarkedRange: boolean;
  selectedSecurity: Security;

  private subscriptionHistoryquoteChanged: Subscription;

  constructor(private timeSeriesQuotesService: TimeSeriesQuotesService,
              private holdingService: HoldingService,
              private messageToastService: MessageToastService,
              private router: Router,
              private dataChangedService: DataChangedService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              activePanelService: ActivePanelService) {
    super(translateService, gps, activePanelService, ['yellow']);
  }

  ngOnInit(): void {
    this.subscriptionHistoryquoteChanged = this.dataChangedService.dateChanged$.subscribe(processedActionData => {
      if (processedActionData.data instanceof Historyquote && processedActionData.action === ProcessedAction.UPDATED) {
        this.readData(true);
      }
    });
    this.readData(false);
  }

  readData(yearChange: boolean): void {
    this.holdingService.getMissingQuotesWithSecurities(this.yearCalendarData.year).subscribe(
      (mqws: MissingQuotesWithSecurities) => {
        this.clearDaySelection(this.selectedDayByUser);
        this.missingQuotesWithSecurities = mqws;
        this.securities = this.missingQuotesWithSecurities.securities;
        this.allMissingDays = [];
        Object.keys(mqws.dateSecurityMissingMap).forEach(e => {
          this.allMissingDays.push(new Date(e));
        });
        this.markGroundDays(this.allMissingDays);
        if (!yearChange) {
          this.setYearsBoundaries(moment(mqws.firstEverTradingDay).year(), moment().year());
        }
      });
  }

  markGroundDays(days: Date[]): void {
    this.yearCalendarData.dates = [];
    days.forEach((date, i) => {
      date.setHours(0, 0, 0, 0);
      this.yearCalendarData.dates.push({
        id: i, start: date, end: date,
        color: 'red',
        select: (range: RangeSelectDays, ranges: RangeSelectDays[]) => this.onDayPlusSelect(range, ranges)
      });
    });
  }

  getEditMenu(): MenuItem[] {
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
    this.foundDayMarkedRange = ranges.length === 2 && this.securityMissingDays.find(date => date === range.start) !== null;
    this.clearCalendarFromTableSelection();
    if (ranges.length === 1 || this.foundDayMarkedRange) {
      if (this.selectedDayByUser) {
        // Remove existing marked day
        this.addRemoveOnOffDay(this.selectedDayByUser);
      }
      this.addRemoveOnOffDay(range.start);
      this.selectedDayByUser = range.start;
      this.selectedDayIdSecurities = this.missingQuotesWithSecurities.dateSecurityMissingMap[
        moment(range.start).format(AppSettings.FORMAT_DATE_SHORT_NATIVE)];
    }
  }

  handleChangedSecurity(security: Security): void {
    this.selectedSecurity = security;
    this.resetMenu();
    this.clearDaySelection(this.selectedDayByUser);
    this.clearCalendarFromTableSelection();
    if (security) {
      Object.entries(this.missingQuotesWithSecurities.dateSecurityMissingMap).forEach(([date, idsSecurity]) => {
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

  getHelpContextId(): HelpIds {
    return HelpIds.HELP_PORTFOLIOS_PERIODPERFORMANCE;
  }

  ngOnDestroy(): void {
    this.subscriptionHistoryquoteChanged && this.subscriptionHistoryquoteChanged.unsubscribe();
  }

  protected getMenuShowOptions(): MenuItem[] {
    let menuItems: MenuItem[] = [];
    if (this.selectedSecurity) {
      menuItems = menuItems.concat(this.timeSeriesQuotesService.getMenuItems(this.selectedSecurity.idSecuritycurrency,
        this.selectedSecurity.currency, false));
      this.contextMenuItems = menuItems;
      menuItems.push(...BusinessHelper.getUrlLinkMenus(this.selectedSecurity));
      TranslateHelper.translateMenuItems(menuItems, this.translateService);
    } else {
      this.contextMenuItems = null;
    }
    return super.getMenuShowOptions().concat(menuItems);
  }

  private clearDaySelection(day: Date): void {
    if (this.selectedDayByUser) {
      this.selectedDayByUser = null;
      this.selectedDayIdSecurities = [];
      this.addRemoveOnOffDay(day);
    }
  }
}

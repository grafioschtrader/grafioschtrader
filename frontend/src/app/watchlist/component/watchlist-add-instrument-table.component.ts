import {ChangeDetectorRef, Component, Input} from '@angular/core';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {WatchlistService} from '../service/watchlist.service';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencyLists} from '../../entities/view/securitycurrency.lists';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ProcessedAction} from '../../shared/types/processed.action';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {Watchlist} from '../../entities/watchlist';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {SecuritycurrencySearchTableBase} from '../../securitycurrency/component/securitycurrency.search.table.base';

@Component({
  selector: 'watchlist-add-instrument-table',
  template: `
    <div class="col-md-12">
      <ng-container *ngFor="let tenantLimit of tenantLimits">
        <h5>{{tenantLimit.actual}} {{tenantLimit.msgKey | translate}} {{tenantLimit.limit}}</h5>
      </ng-container>
      <p-table [columns]="fields" [value]="securitycurrencyList" [(selection)]="selectedSecuritycurrencies"
               dataKey="idSecuritycurrency" [paginator]="true" [rows]="10" [rowsPerPageOptions]="[10,20,30]"
               sortMode="multiple" [multiSortMeta]="multiSortMeta"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th style="width: 2.25em">
              <p-tableHeaderCheckbox></p-tableHeaderCheckbox>
            </th>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td>
              <p-tableCheckbox [value]="el"></p-tableCheckbox>
            </td>
            <ng-container *ngFor="let field of fields">
              <td *ngIf="field.visible" [style.width.px]="field.width"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                {{getValueByPath(el, field)}}
              </td>
            </ng-container>
          </tr>
        </ng-template>
        <ng-template pTemplate="paginatorleft" let-state>
          {{selectedSecuritycurrencies.length}} {{'SELECTED_FROM' | translate}} {{securitycurrencyList.length}}
          {{"ALLOWED" | translate}}: <span *ngFor="let tenantLimit of tenantLimits"
                                           [style.color]='selectedSecuritycurrencies.length>
                                            tenantLimit.limit - tenantLimit.actual? "red": "green"'>
          {{tenantLimit.limit - tenantLimit.actual - selectedSecuritycurrencies.length}},</span>
        </ng-template>
      </p-table>
    </div>

    <div class="ui-dialog-buttonpane ui-widget-content ui-helper-clearfix">
      <button pButton [disabled]="selectedSecuritycurrencies.length === 0 || reachedWatchlistLimits()"
              class="btn pull-right" [label]="'ADD' | translate"
              (click)="onClickAdd()" type="button">
      </button>
    </div>
  `
})
export class WatchlistAddInstrumentTableComponent extends SecuritycurrencySearchTableBase {
  @Input() tenantLimits: TenantLimit[];

  selectedSecuritycurrencies: (Security | Currencypair)[] = [];

  idWatchlist: number;
  securitycurrencySearch: SecuritycurrencySearch;
  maxWatchlistLength: number;


  constructor(private dataChangedService: DataChangedService,
              private watchlistService: WatchlistService,
              changeDetectionStrategy: ChangeDetectorRef,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(changeDetectionStrategy, usersettingsService, translateService, globalparameterService);
    this.multiSortMeta.push({field: 'name', order: 1});
  }

  clearList(): void {
    this.securitycurrencyList = [];
    this.selectedSecuritycurrencies = [];
  }

  loadData(idWatchlist: number, securitycurrencySearch: SecuritycurrencySearch): void {
    this.idWatchlist = idWatchlist;
    this.securitycurrencySearch = securitycurrencySearch;

    this.watchlistService.searchByCriteria(idWatchlist, securitycurrencySearch)
      .subscribe((securitycurrencyLists: SecuritycurrencyLists) => {
        this.createTranslatedValueStoreAndFilterField(securitycurrencyLists.securityList);
        this.securitycurrencyList = securitycurrencyLists.securityList;
        this.transformCurrencypairToCurrencypairWatchlist(securitycurrencyLists.currencypairList);
        this.changeDetectionStrategy.markForCheck();
      });
  }


  onClickAdd(): void {
    const securitycurrencyLists = new SecuritycurrencyLists();
    this.selectedSecuritycurrencies.forEach(securitycurrency => {
      if (securitycurrency instanceof CurrencypairWatchlist) {
        securitycurrencyLists.currencypairList.push(Object.assign(new Currencypair(null, null), securitycurrency));
      } else {
        securitycurrencyLists.securityList.push(Object.assign(new Security(), securitycurrency));
      }
    });
    this.watchlistService.addSecuritycurrenciesToWatchlist(this.idWatchlist, securitycurrencyLists).subscribe(watchlist => {
      this.clearList();
      this.loadData(this.idWatchlist, this.securitycurrencySearch);
      this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.UPDATED, new Watchlist()));
    });
  }

  reachedWatchlistLimits(): boolean {
    for (const tenantLimit of this.tenantLimits) {
      if (tenantLimit.actual + this.selectedSecuritycurrencies.length > tenantLimit.limit) {
        return true;
      }
    }
    return false;
  }
}

import { ChangeDetectionStrategy, Component, OnInit, Injector } from '@angular/core';
import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FilterService, ConfirmationService } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { WatchlistIndicatorService } from '../service/watchlist-indicator.service';
import { WatchlistIndicatorColumn } from '../model/watchlist-indicator';
import { WatchlistIndicatorColumnEditComponent } from './watchlist-indicator-column-edit.component';
import { WatchlistService } from '../service/watchlist.service';
import { forkJoin } from 'rxjs';
import { HelpIds } from '../../lib/help/help.ids';

/** Ordered column editor, separate from both the report and its dynamic edit form. */
@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TableModule, ButtonModule, TooltipModule, TranslateModule, WatchlistIndicatorColumnEditComponent],
  styles: [
    `
      .toolbar {
        margin-bottom: 0.75rem;
      }
      .row-actions {
        white-space: nowrap;
      }
    `
  ],
  template: `
    <div class="toolbar d-flex align-items-center">
      <p-button [label]="'WIC_CREATE_COLUMN' | translate" (click)="edit(null)">
        <i class="pi pi-plus" pButtonIcon></i>
      </p-button>
      <p-button class="ms-auto" [rounded]="true" (click)="helpLink()">
        <i class="pi pi-question" pButtonIcon></i>
      </p-button>
    </div>
    <p-table [value]="rows" (onRowReorder)="reorder()" dataKey="idWatchlistIndicatorColumn">
      <ng-template #header
        ><tr>
          <th></th>
          @for (field of fields; track field.field) {
            <th>{{ field.headerTranslated }}</th>
          }
          <th></th></tr
      ></ng-template>
      <ng-template #body let-row let-index="rowIndex"
        ><tr [pReorderableRow]="index">
          <td><span class="pi pi-bars" pReorderableRowHandle></span></td>
          @for (field of fields; track field.field) {
            <td>{{ getValueByPath(row, field) }}</td>
          }
          <td class="row-actions">
            <button
              pButton
              type="button"
              class="p-button-rounded p-button-text"
              [attr.aria-label]="'EDIT' | translate"
              [pTooltip]="'EDIT' | translate"
              (click)="edit(row)">
              <i class="pi pi-pencil" pButtonIcon></i>
            </button>
            <button
              pButton
              type="button"
              class="p-button-rounded p-button-text p-button-danger"
              [attr.aria-label]="'DELETE' | translate"
              [pTooltip]="'DELETE' | translate"
              (click)="remove(row)">
              <i class="pi pi-trash" pButtonIcon></i>
            </button>
          </td></tr
      ></ng-template>
    </p-table>
    @if (visibleEdit) {
      <watchlist-indicator-column-edit [visibleDialog]="true" [callParam]="callParam" (closeDialog)="closed()" />
    }
  `
})
export class WatchlistIndicatorColumnsDialogComponent extends TableConfigBase implements OnInit {
  rows: WatchlistIndicatorColumn[] = [];
  visibleEdit = false;
  callParam: any;
  constructor(
    filter: FilterService,
    settings: UserSettingsService,
    translate: TranslateService,
    gps: GlobalparameterService,
    injector: Injector,
    private service: WatchlistIndicatorService,
    private watchlists: WatchlistService,
    private confirmation: ConfirmationService
  ) {
    super(filter, settings, translate, gps, injector);
    this.addColumn(DataType.String, 'label', 'WIC_LABEL', true, false);
    this.addColumnFeqH(DataType.String, 'expression', true, false);
    this.addColumn(DataType.String, 'watchlistName', 'ID_WATCHLIST', true, false);
    this.prepareTableAndTranslate();
  }
  ngOnInit(): void {
    this.load();
  }
  private load(): void {
    forkJoin([this.service.getAll(), this.watchlists.getWatchlistsByIdTenant()]).subscribe(([rows, lists]) => {
      this.rows = rows.map((row) =>
        Object.assign(row, {
          watchlistName:
            lists.find((w) => w.idWatchlist === row.idWatchlist)?.name ??
            this.translateService.instant('WIC_ALL_WATCHLISTS')
        })
      );
    });
  }
  edit(row: WatchlistIndicatorColumn): void {
    forkJoin([
      this.gps.getEntityFormDefinition('WatchlistIndicatorColumn'),
      this.watchlists.getWatchlistsByIdTenant(),
      this.service.markColors(),
      this.service.benchmarks()
    ]).subscribe(([formDefinition, watchlists, colors, benchmarks]) => {
      this.callParam = { thisObject: row, optParam: { formDefinition, watchlists, colors, benchmarks } };
      this.visibleEdit = true;
    });
  }
  closed(): void {
    this.visibleEdit = false;
    this.load();
  }
  reorder(): void {
    this.service
      .order(this.rows.map((r) => r.idWatchlistIndicatorColumn))
      .subscribe({ next: () => this.load(), error: () => this.load() });
  }
  /** Opens the user manual page of the indicator tab, which explains expressions, functions and variables. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_WATCHLIST_INDICATOR);
  }
  remove(row: WatchlistIndicatorColumn): void {
    this.confirmation.confirm({
      message: this.translateService.instant('WIC_DELETE_CONFIRM', { label: row.label }),
      accept: () => this.service.deleteEntity(row.idWatchlistIndicatorColumn).subscribe(() => this.load())
    });
  }
}

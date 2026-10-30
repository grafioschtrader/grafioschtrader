import { Component, signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { plainToInstance } from 'class-transformer';
import { Subscription } from 'rxjs';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { TableCrudSupportMenu } from '../../lib/datashowbase/table.crud.support.menu';
import { AppSettings } from '../../shared/app.settings';
import { HelpIds } from '../../lib/help/help.ids';
import { BankruptSecurity, BankruptSecurityRow } from '../../entities/bankrupt.security';
import { BankruptSecurityService } from '../service/bankrupt.security.service';
import { BankruptSecurityEditComponent } from './bankrupt-security-edit.component';
import { BankruptSecurityCallParam } from './bankrupt.security.call.param';

/**
 * The instruments whose issuer no longer supplies price data.
 *
 * <p>
 * The two closing dates are the point of the table. When the newest filled price is younger than the newest price a
 * provider delivered, the end of day job is keeping the history complete; when the two are equal and old, it is not,
 * and the reader can see that without opening anything.
 * </p>
 *
 * <p>
 * Marking is not a client decision but a statement about a shared instrument, so a row may be changed only by whoever
 * may change the instrument itself. The menu entries are bound to that right rather than left to fail in the backend.
 * </p>
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [scrollHeight]="'flex'"
      [scrollable]="true"
      [containerClass]="{
        'data-container-full': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems()"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ entityNameUpper | translate }}</h4>
    </configurable-table>

    @if (visibleDialog()) {
      <bankrupt-security-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam()"
        (closeDialog)="handleCloseDialog($event)" />
    }
  `,
  providers: [DialogService],
  imports: [TranslateModule, ConfigurableTableComponent, BankruptSecurityEditComponent]
})
export class BankruptSecurityTableComponent extends TableCrudSupportMenu<BankruptSecurity> {
  private bankruptSecurityService: BankruptSecurityService = inject(BankruptSecurityService);

  readonly callParam = signal<BankruptSecurityCallParam | null>(null);

  private readDataSub?: Subscription;
  private openDialogSub?: Subscription;

  constructor() {
    super(AppSettings.BANKRUPT_SECURITY, inject(BankruptSecurityService));
    this.addColumn(DataType.String, 'name', 'SECURITY', true, false, { filterType: FilterType.likeDataType });
    this.addColumnFeqH(DataType.String, 'isin', true, false, { filterType: FilterType.likeDataType });
    this.addColumnFeqH(DataType.String, 'currency', true, false, { filterType: FilterType.withOptions });
    this.addColumnFeqH(DataType.DateString, 'noDataSince', true, false);
    this.addColumnFeqH(DataType.DateString, 'noTradingSince', true, false);
    this.addColumnFeqH(DataType.DateString, 'lastRealQuoteDate', true, false);
    this.addColumnFeqH(DataType.DateString, 'lastQuoteDate', true, false);
    this.addColumnFeqH(DataType.String, 'note', true, false);
    this.multiSortMeta.push({ field: 'name', order: 1 });
    this.prepareTableAndTranslate();
  }

  readData(): void {
    this.readDataSub?.unsubscribe();
    this.readDataSub = this.bankruptSecurityService
      .getAllWithSecurityName()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => {
        const entities = plainToInstance(BankruptSecurityRow, rows);
        this.createTranslatedValueStoreAndFilterField(entities);
        this.prepareFilter(entities);
        this.entityList.set(entities);
        this.refreshSelectedEntity();
      });
  }

  /**
   * Fetches the form definition before the dialog is shown. It is memoised, so only the first open costs a request and
   * the dialog can build its form synchronously, which is what makes the edit values transfer at all.
   */
  override handleEditEntity(entity: BankruptSecurity | null): void {
    this.openDialogSub?.unsubscribe();
    const row = entity ? plainToInstance(BankruptSecurityRow, entity) : null;
    this.openDialogSub = this.gps
      .getEntityFormDefinition(AppSettings.BANKRUPT_SECURITY)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((formDefinition) => {
        this.callParam.set(new BankruptSecurityCallParam(formDefinition, row, row?.name ?? null));
        this.visibleDialog.set(true);
      });
  }

  /** A marker belongs to whoever may edit the instrument; the backend refuses anything else outright. */
  protected override hasRightsForUpdateEntity(entity: BankruptSecurity): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteEntity(this.gps, entity);
  }

  protected override prepareCallParam(entity: BankruptSecurity | null): void {
    // Not used: handleEditEntity is overridden because the form definition has to arrive before the dialog is shown.
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_BANKRUPT_SECURITY;
  }
}

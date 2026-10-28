import { Component, Injector, Input, OnChanges, OnDestroy, signal, SimpleChanges, output } from '@angular/core';

import { ConfirmationService, FilterService, MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AngularSvgIconModule, SvgIconRegistryService } from 'angular-svg-icon';
import { combineLatest, Subscription } from 'rxjs';
import { plainToClass } from 'class-transformer';
import { HistoryquoteTableBase } from './historyquote-table.base';
import { HistoryquoteLegacy } from '../../entities/historyquote.legacy';
import { Securitycurrency } from '../../entities/securitycurrency';
import { Security } from '../../entities/security';
import { Currencypair } from '../../entities/currencypair';
import { CrudMenuOptions } from '../../lib/datashowbase/table.crud.support.menu';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { HistoryquoteService } from '../service/historyquote.service';
import { HistoryquoteLegacyService } from '../service/historyquote.legacy.service';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import {
  FileUploadParam,
  SupportedCSVFormats,
  UploadServiceFunction
} from '../../lib/generaldialog/model/file.upload.param';
import { UploadFileDialogComponent } from '../../lib/generaldialog/component/upload-file-dialog.component';
import { HistoryquoteLegacyApplySplitDialogComponent } from './historyquote-legacy-apply-split-dialog.component';
import { HistoryquoteLegacyEditComponent } from './historyquote-legacy-edit.component';
import { HistoryquoteLegacySecurityCurrency } from '../model/historyquote.legacy.security.currency';

/**
 * Displays archived {@code historyquote_legacy} rows for one security. Hosted by
 * {@link HistoryquoteHostComponent} via an @Input switch from the live table view. Individual rows can be edited
 * (through the propose-change flow) and deleted, but not created. The context menu also offers: return to the live
 * view, apply a forgotten split to all archived rows before a given date, delete the entire archive, CSV export and
 * CSV import.
 */
@Component({
  selector: 'historyquote-legacy',
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      #cmDiv
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <div class="datatable">
        <configurable-table
          [data]="entityList()"
          [fields]="fields"
          [fieldsRevision]="fieldsRevision()"
          [dataKey]="entityKeyName"
          [selectionMode]="'single'"
          [(selection)]="selectedEntity"
          [multiSortMeta]="multiSortMeta"
          [customSortFn]="customSorter"
          [paginator]="true"
          [rows]="20"
          [stripedRows]="true"
          [showGridlines]="true"
          [hasFilter]="hasFilter"
          [containerClass]="''"
          [contextMenuEnabled]="!!contextMenuItems()"
          [showContextMenu]="isActivated()"
          [contextMenuItems]="contextMenuItems()"
          [valueGetterFn]="valueGetter">
          <div caption>
            <h4>{{ 'HISTORYQUOTE_LEGACY' | translate }}</h4>
          </div>

          <!-- Custom icon cell template for svg-icon rendering of createType column -->
          <ng-template #iconCell let-row let-field="field" let-value="value">
            <svg-icon [name]="value" [svgStyle]="{ 'width.px': 14, 'height.px': 14 }" />
          </ng-template>
        </configurable-table>
      </div>
    </div>

    @if (visibleDialog()) {
      <historyquote-legacy-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam"
        (closeDialog)="handleCloseDialog($event)" />
    }

    @if (visibleApplySplitDialog()) {
      <historyquote-legacy-apply-split-dialog
        [visibleDialog]="visibleApplySplitDialog()"
        [idSecuritycurrency]="idSecuritycurrency"
        (closeDialog)="handleSplitDialogClose($event)" />
    }

    @if (visibleUploadFileDialog()) {
      <upload-file-dialog
        [visibleDialog]="visibleUploadFileDialog()"
        [fileUploadParam]="fileUploadParam"
        (closeDialog)="handleUploadDialogClose($event)" />
    }
  `,
  providers: [DialogService],
  imports: [
    TranslateModule,
    AngularSvgIconModule,
    ConfigurableTableComponent,
    HistoryquoteLegacyEditComponent,
    HistoryquoteLegacyApplySplitDialogComponent,
    UploadFileDialogComponent
  ]
})
export class HistoryquoteLegacyComponent
  extends HistoryquoteTableBase<HistoryquoteLegacy>
  implements OnChanges, OnDestroy
{
  @Input() idSecuritycurrency: number;
  readonly showLiveRequested = output<void>();

  callParam: HistoryquoteLegacySecurityCurrency;
  readonly visibleApplySplitDialog = signal(false);
  readonly visibleUploadFileDialog = signal(false);
  fileUploadParam: FileUploadParam;

  private securitycurrency: Securitycurrency;
  private supportedCSVFormats: SupportedCSVFormats;
  /** Requests of the shown instrument and of its rows; a newer one replaces a request that is still pending. */
  private instrumentSubscription: Subscription;
  private rowsSubscription: Subscription;
  private readonly legacyUploadAdapter: UploadServiceFunction = {
    uploadFiles: (id, fd) => this.historyquoteLegacyService.uploadFilesLegacy(id, fd)
  };

  constructor(
    private historyquoteService: HistoryquoteService,
    private historyquoteLegacyService: HistoryquoteLegacyService,
    private securityService: SecurityService,
    private currencypairService: CurrencypairService,
    private iconReg: SvgIconRegistryService,
    usersettingsService: UserSettingsService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    gps: GlobalparameterService,
    translateService: TranslateService,
    injector: Injector
  ) {
    super(
      AppSettings.HISTORYQUOTE_LEGACY,
      historyquoteLegacyService,
      confirmationService,
      messageToastService,
      activePanelService,
      dialogService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector,
      [CrudMenuOptions.Allow_Edit, CrudMenuOptions.Allow_Delete]
    );

    HistoryquoteTableBase.registerCreateTypeIcons(this.iconReg);
    this.addDateColumn();
    this.addColumnFeqH(DataType.DateString, 'transferDate', true, false, {
      export: true
    });
    this.addCreateTypeColumn();
    this.addOhlcvColumns();
    this.applyDefaultDateSort();
    this.prepareTableAndTranslate();
    this.destroyRef.onDestroy(() => {
      this.instrumentSubscription?.unsubscribe();
      this.rowsSubscription?.unsubscribe();
    });
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_HISTORYQUOTES_LEGACY;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['idSecuritycurrency'] && this.idSecuritycurrency != null) {
      this.loadSecuritycurrencyAndData();
    }
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  readData(): void {
    if (this.idSecuritycurrency == null) {
      return;
    }
    this.rowsSubscription?.unsubscribe();
    this.rowsSubscription = this.historyquoteLegacyService
      .getLegacyForSecurity(this.idSecuritycurrency)
      .subscribe((rows) => {
        this.entityList.set(rows ?? []);
        this.refreshSelectedEntity();
      });
  }

  /** The legacy specific actions after the CRUD items, untranslated. */
  private createLegacyMenuItems(): MenuItem[] {
    return [
      {
        label: 'HISTORYQUOTE_SHOW_LIVE',
        command: () => this.showLiveRequested.emit()
      },
      {
        label: 'HISTORYQUOTE_LEGACY_APPLY_SPLIT',
        command: () => this.visibleApplySplitDialog.set(true)
      },
      {
        label: 'HISTORYQUOTE_LEGACY_DELETE_ALL',
        command: () => this.confirmDeleteAll()
      },
      { separator: true },
      {
        label: 'IMPORT_QUOTES' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.uploadImportLegacy()
      },
      {
        label: 'EXPORT_CSV',
        command: () => this.downloadCSvFile(this.entityList())
      }
    ];
  }

  override resetMenu(entity: HistoryquoteLegacy): void {
    const editMenuItems = this.prepareEditMenu(entity) ?? [];
    const specItems = this.createLegacyMenuItems();
    this.publishTableMenu(
      entity,
      editMenuItems.length > 0 ? [...editMenuItems, { separator: true }, ...specItems] : specItems
    );
  }

  handleSplitDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleApplySplitDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  uploadImportLegacy(): void {
    if (this.supportedCSVFormats) {
      this.openUploadDialog();
      return;
    }
    this.historyquoteService.getPossibleCSVFormats().subscribe((formats) => {
      this.supportedCSVFormats = formats;
      this.openUploadDialog();
    });
  }

  handleUploadDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleUploadFileDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  private openUploadDialog(): void {
    this.fileUploadParam = new FileUploadParam(
      HelpIds.HELP_WATCHLIST_HISTORYQUOTES,
      null,
      'csv',
      'IMPORT_QUOTES',
      false,
      this.legacyUploadAdapter,
      this.idSecuritycurrency,
      this.supportedCSVFormats,
      BaseSettings.CSV_EXPORT_FORMAT
    );
    this.visibleUploadFileDialog.set(true);
  }

  protected prepareCallParam(entity: HistoryquoteLegacy): void {
    this.callParam = new HistoryquoteLegacySecurityCurrency(
      plainToClass(HistoryquoteLegacy, entity),
      this.securitycurrency
    );
  }

  protected override getId(entity: HistoryquoteLegacy): number {
    return entity.idHistoryquoteLegacy;
  }

  protected override beforeDelete(entity: HistoryquoteLegacy): HistoryquoteLegacy {
    return plainToClass(HistoryquoteLegacy, entity);
  }

  // hasRightsForUpdateEntity is intentionally NOT overridden — mirroring the live HistoryquoteTableComponent,
  // the Edit menu item stays reachable for propose-change users so they can submit a change request through
  // the edit dialog (configured by AuditHelper.configureFormFromAuditableRights). Only Delete is guarded.
  protected override hasRightsForDeleteEntity(entity: HistoryquoteLegacy): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps, this.securitycurrency);
  }

  private loadSecuritycurrencyAndData(): void {
    const cpObservable = this.currencypairService.getCurrencypairByIdSecuritycurrency(this.idSecuritycurrency);
    const securityObservable = this.securityService.getSecurityByIdSecuritycurrency(this.idSecuritycurrency);
    this.instrumentSubscription?.unsubscribe();
    this.instrumentSubscription = combineLatest([cpObservable, securityObservable]).subscribe((data: any[]) => {
      this.securitycurrency = data[0]
        ? Object.assign(new Currencypair(), data[0])
        : Object.assign(new Security(), data[1]);
      this.readData();
    });
  }

  private confirmDeleteAll(): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'HISTORYQUOTE_LEGACY_DELETE_ALL_CONFIRM',
      () => {
        this.historyquoteLegacyService.deleteAllLegacyForSecurity(this.idSecuritycurrency).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'HISTORYQUOTE_LEGACY_DELETE_ALL');
          this.entityList.set([]);
          this.showLiveRequested.emit();
        });
      }
    );
  }
}

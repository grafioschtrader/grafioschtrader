import { requireInitialized } from '../../lib/helper/required-state';
import { Component, effect, inject, input, OnDestroy, output, signal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';

import { TranslateModule } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { AngularSvgIconModule, SvgIconRegistryService } from 'angular-svg-icon';
import { plainToClass } from 'class-transformer';
import { combineLatest, map, switchMap } from 'rxjs';
import { Currencypair } from '../../entities/currencypair';
import { HistoryquoteLegacy } from '../../entities/historyquote.legacy';
import { Security } from '../../entities/security';
import { Securitycurrency } from '../../entities/securitycurrency';
import { BaseSettings } from '../../lib/base.settings';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { CrudMenuOptions } from '../../lib/datashowbase/table.crud.support.menu';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { UploadFileDialogComponent } from '../../lib/generaldialog/component/upload-file-dialog.component';
import {
  FileUploadParam,
  SupportedCSVFormats,
  UploadServiceFunction
} from '../../lib/generaldialog/model/file.upload.param';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { AppSettings } from '../../shared/app.settings';
import { HistoryquoteLegacySecurityCurrency } from '../model/historyquote.legacy.security.currency';
import { HistoryquoteLegacyService } from '../service/historyquote.legacy.service';
import { HistoryquoteService } from '../service/historyquote.service';
import { HistoryquoteLegacyApplySplitDialogComponent } from './historyquote-legacy-apply-split-dialog.component';
import { HistoryquoteLegacyEditComponent } from './historyquote-legacy-edit.component';
import { HistoryquoteTableBase } from './historyquote-table.base';

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
          [loading]="legacyResource.isLoading()"
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
        [idSecuritycurrency]="idSecuritycurrency()"
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
export class HistoryquoteLegacyComponent extends HistoryquoteTableBase<HistoryquoteLegacy> implements OnDestroy {
  private historyquoteService: HistoryquoteService = inject(HistoryquoteService);
  private historyquoteLegacyService: HistoryquoteLegacyService = inject(HistoryquoteLegacyService);
  private securityService: SecurityService = inject(SecurityService);
  private currencypairService: CurrencypairService = inject(CurrencypairService);
  private iconReg: SvgIconRegistryService = inject(SvgIconRegistryService);

  readonly idSecuritycurrency = input.required<number>();
  readonly showLiveRequested = output<void>();

  private initializedCallParam?: HistoryquoteLegacySecurityCurrency;

  get callParam(): HistoryquoteLegacySecurityCurrency {
    return requireInitialized(this.initializedCallParam, 'HistoryquoteLegacyComponent.callParam');
  }

  set callParam(value: HistoryquoteLegacySecurityCurrency) {
    this.initializedCallParam = value;
  }
  readonly visibleApplySplitDialog = signal(false);
  readonly visibleUploadFileDialog = signal(false);
  private initializedFileUploadParam?: FileUploadParam;

  get fileUploadParam(): FileUploadParam {
    return requireInitialized(this.initializedFileUploadParam, 'HistoryquoteLegacyComponent.fileUploadParam');
  }

  set fileUploadParam(value: FileUploadParam) {
    this.initializedFileUploadParam = value;
  }

  private securitycurrency?: Securitycurrency;
  private supportedCSVFormats?: SupportedCSVFormats;
  private readonly legacyUploadAdapter: UploadServiceFunction = {
    uploadFiles: (id, fd) => this.historyquoteLegacyService.uploadFilesLegacy(id, fd)
  };

  readonly legacyResource = rxResource({
    params: () => this.idSecuritycurrency(),
    stream: ({ params: id }) =>
      combineLatest([
        this.currencypairService.getCurrencypairByIdSecuritycurrency(id),
        this.securityService.getSecurityByIdSecuritycurrency(id)
      ]).pipe(
        switchMap(([pair, security]) =>
          this.historyquoteLegacyService.getLegacyForSecurity(id).pipe(
            map((rows) => ({
              instrument: pair ? Object.assign(new Currencypair(), pair) : Object.assign(new Security(), security),
              rows
            }))
          )
        )
      )
  });
  constructor() {
    super(AppSettings.HISTORYQUOTE_LEGACY, inject(HistoryquoteLegacyService), [
      CrudMenuOptions.Allow_Edit,
      CrudMenuOptions.Allow_Delete
    ]);

    HistoryquoteTableBase.registerCreateTypeIcons(this.iconReg);
    this.addDateColumn();
    this.addColumnFeqH(DataType.DateString, 'transferDate', true, false, {
      export: true
    });
    this.addCreateTypeColumn();
    this.addOhlcvColumns();
    this.applyDefaultDateSort();
    this.prepareTableAndTranslate();

    reportResourceErrors(this.legacyResource, this.historyquoteLegacyService);
    effect(() => {
      if (this.legacyResource.isLoading()) return;
      const result = this.legacyResource.hasValue() ? this.legacyResource.value() : undefined;
      untracked(() => {
        if (result) this.securitycurrency = result.instrument;
        this.entityList.set(result?.rows ?? []);
        this.refreshSelectedEntity();
      });
    });
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_HISTORYQUOTES_LEGACY;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  readData(): void {
    this.legacyResource.reload();
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

  override resetMenu(entity: HistoryquoteLegacy | null): void {
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
      this.idSecuritycurrency(),
      requireInitialized(this.supportedCSVFormats, 'HistoryquoteLegacyComponent.supportedCSVFormats'),
      BaseSettings.CSV_EXPORT_FORMAT
    );
    this.visibleUploadFileDialog.set(true);
  }

  protected prepareCallParam(entity: HistoryquoteLegacy | null): void {
    this.callParam = new HistoryquoteLegacySecurityCurrency(
      plainToClass(HistoryquoteLegacy, entity),
      requireInitialized(this.securitycurrency, 'HistoryquoteLegacyComponent.securitycurrency')
    );
  }

  protected override getId(entity: HistoryquoteLegacy): number | undefined {
    return entity.idHistoryquoteLegacy;
  }

  protected override beforeDelete(entity: HistoryquoteLegacy): HistoryquoteLegacy {
    return plainToClass(HistoryquoteLegacy, entity);
  }

  // hasRightsForUpdateEntity is intentionally NOT overridden — mirroring the live HistoryquoteTableComponent,
  // the Edit menu item stays reachable for propose-change users so they can submit a change request through
  // the edit dialog (configured by AuditHelper.configureFormFromAuditableRights). Only Delete is guarded.
  protected override hasRightsForDeleteEntity(entity: HistoryquoteLegacy): boolean {
    return (
      this.securitycurrency != null && AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps, this.securitycurrency)
    );
  }

  private confirmDeleteAll(): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'HISTORYQUOTE_LEGACY_DELETE_ALL_CONFIRM',
      () => {
        this.historyquoteLegacyService.deleteAllLegacyForSecurity(this.idSecuritycurrency()).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'HISTORYQUOTE_LEGACY_DELETE_ALL');
          this.entityList.set([]);
          this.showLiveRequested.emit();
        });
      }
    );
  }
}

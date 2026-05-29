import {Component, EventEmitter, Injector, Input, OnChanges, OnDestroy, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AngularSvgIconModule, SvgIconRegistryService} from 'angular-svg-icon';
import {combineLatest} from 'rxjs';
import {plainToClass} from 'class-transformer';
import {HistoryquoteTableBase} from './historyquote-table.base';
import {HistoryquoteLegacy} from '../../entities/historyquote.legacy';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {CrudMenuOptions} from '../../lib/datashowbase/table.crud.support.menu';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AppHelper} from '../../lib/helper/app.helper';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {HistoryquoteService} from '../service/historyquote.service';
import {HistoryquoteLegacyService} from '../service/historyquote.legacy.service';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {
  FileUploadParam,
  SupportedCSVFormats,
  UploadServiceFunction
} from '../../lib/generaldialog/model/file.upload.param';
import {UploadFileDialogComponent} from '../../lib/generaldialog/component/upload-file-dialog.component';
import {
  HistoryquoteLegacyApplySplitDialogComponent
} from './historyquote-legacy-apply-split-dialog.component';
import {
  HistoryquoteLegacyEditComponent,
  HistoryquoteLegacySecurityCurrency
} from './historyquote-legacy-edit.component';

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
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable">
        <configurable-table
          [data]="entityList"
          [fields]="fields"
          [dataKey]="entityKeyName"
          [selectionMode]="'single'"
          [(selection)]="selectedEntity"
          [multiSortMeta]="multiSortMeta"
          [customSortFn]="customSort.bind(this)"
          [paginator]="true"
          [rows]="20"
          [stripedRows]="true"
          [showGridlines]="true"
          [hasFilter]="hasFilter"
          [containerClass]="''"
          [contextMenuEnabled]="!!contextMenuItems"
          [showContextMenu]="isActivated()"
          [contextMenuItems]="contextMenuItems"
          [valueGetterFn]="getValueByPath.bind(this)">

          <div caption>
            <h4>{{ 'HISTORYQUOTE_LEGACY' | translate }}</h4>
          </div>

          <!-- Custom icon cell template for svg-icon rendering of createType column -->
          <ng-template #iconCell let-row let-field="field" let-value="value">
            <svg-icon [name]="value" [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
          </ng-template>

        </configurable-table>
      </div>
    </div>

    @if (visibleDialog) {
      <historyquote-legacy-edit [visibleDialog]="visibleDialog"
                                [callParam]="callParam"
                                (closeDialog)="handleCloseDialog($event)">
      </historyquote-legacy-edit>
    }

    @if (visibleApplySplitDialog) {
      <historyquote-legacy-apply-split-dialog [visibleDialog]="visibleApplySplitDialog"
                                              [idSecuritycurrency]="idSecuritycurrency"
                                              (closeDialog)="handleSplitDialogClose($event)">
      </historyquote-legacy-apply-split-dialog>
    }

    @if (visibleUploadFileDialog) {
      <upload-file-dialog [visibleDialog]="visibleUploadFileDialog"
                          [fileUploadParam]="fileUploadParam"
                          (closeDialog)="handleUploadDialogClose($event)">
      </upload-file-dialog>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    AngularSvgIconModule,
    ConfigurableTableComponent,
    HistoryquoteLegacyEditComponent,
    HistoryquoteLegacyApplySplitDialogComponent,
    UploadFileDialogComponent
  ]
})
export class HistoryquoteLegacyComponent extends HistoryquoteTableBase<HistoryquoteLegacy>
  implements OnChanges, OnDestroy {
  @Input() idSecuritycurrency: number;
  @Output() showLiveRequested = new EventEmitter<void>();

  callParam: HistoryquoteLegacySecurityCurrency;
  visibleApplySplitDialog = false;
  visibleUploadFileDialog = false;
  fileUploadParam: FileUploadParam;

  private securitycurrency: Securitycurrency;
  private legacySpecMenuItems: MenuItem[];
  private supportedCSVFormats: SupportedCSVFormats;
  private readonly legacyUploadAdapter: UploadServiceFunction = {
    uploadFiles: (id, fd) => this.historyquoteLegacyService.uploadFilesLegacy(id, fd)
  };

  constructor(private historyquoteService: HistoryquoteService,
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
              injector: Injector) {
    super(AppSettings.HISTORYQUOTE_LEGACY, historyquoteLegacyService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService, injector,
      [CrudMenuOptions.Allow_Edit, CrudMenuOptions.Allow_Delete]);

    HistoryquoteTableBase.registerCreateTypeIcons(this.iconReg);
    this.addDateColumn();
    this.addColumnFeqH(DataType.DateString, 'transferDate', true, false, {export: true});
    this.addCreateTypeColumn();
    this.addOhlcvColumns();
    this.applyDefaultDateSort();
    this.prepareTableAndTranslate();
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
    this.historyquoteLegacyService.getLegacyForSecurity(this.idSecuritycurrency).subscribe(rows => {
      this.entityList = rows ?? [];
      this.prepareMenu();
      this.refreshSelectedEntity();
    });
  }

  prepareMenu(): void {
    this.legacySpecMenuItems = [
      {label: 'HISTORYQUOTE_SHOW_LIVE', command: () => this.showLiveRequested.emit()},
      {label: 'HISTORYQUOTE_LEGACY_APPLY_SPLIT', command: () => this.visibleApplySplitDialog = true},
      {label: 'HISTORYQUOTE_LEGACY_DELETE_ALL', command: () => this.confirmDeleteAll()},
      {separator: true},
      {label: 'IMPORT_QUOTES' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.uploadImportLegacy()},
      {label: 'EXPORT_CSV', command: () => this.downloadCSvFile(this.entityList)}
    ];
    TranslateHelper.translateMenuItems(this.legacySpecMenuItems, this.translateService);
  }

  override resetMenu(entity: HistoryquoteLegacy): void {
    this.selectedEntity = entity;
    const editMenuItems = this.prepareEditMenu(entity) ?? [];
    const specItems = this.legacySpecMenuItems ?? [];
    this.contextMenuItems = editMenuItems.length > 0
      ? [...editMenuItems, {separator: true}, ...specItems]
      : [...specItems];
    this.activePanelService.activatePanel(this, {editMenu: this.contextMenuItems});
  }

  handleSplitDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleApplySplitDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  uploadImportLegacy(): void {
    if (this.supportedCSVFormats) {
      this.openUploadDialog();
      return;
    }
    this.historyquoteService.getPossibleCSVFormats().subscribe(formats => {
      this.supportedCSVFormats = formats;
      this.openUploadDialog();
    });
  }

  handleUploadDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleUploadFileDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  private openUploadDialog(): void {
    this.fileUploadParam = new FileUploadParam(HelpIds.HELP_WATCHLIST_HISTORYQUOTES, null, 'csv', 'IMPORT_QUOTES',
      false, this.legacyUploadAdapter, this.idSecuritycurrency, this.supportedCSVFormats,
      BaseSettings.CSV_EXPORT_FORMAT);
    this.visibleUploadFileDialog = true;
  }

  protected prepareCallParam(entity: HistoryquoteLegacy): void {
    this.callParam = new HistoryquoteLegacySecurityCurrency(plainToClass(HistoryquoteLegacy, entity),
      this.securitycurrency);
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
    combineLatest([cpObservable, securityObservable]).subscribe((data: any[]) => {
      this.securitycurrency = data[0]
        ? Object.assign(new Currencypair(), data[0])
        : Object.assign(new Security(), data[1]);
      this.readData();
    });
  }

  private confirmDeleteAll(): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'HISTORYQUOTE_LEGACY_DELETE_ALL_CONFIRM', () => {
        this.historyquoteLegacyService.deleteAllLegacyForSecurity(this.idSecuritycurrency).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'HISTORYQUOTE_LEGACY_DELETE_ALL');
          this.entityList = [];
          this.showLiveRequested.emit();
        });
      });
  }
}

import {ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {ContextMenuModule} from 'primeng/contextmenu';

import {TableEditConfigBase} from '../../../lib/datashowbase/table.edit.config.base';
import {EditableTableComponent, RowEditSaveEvent} from '../../../lib/datashowbase/editable-table.component';
import {GlobalparameterService} from '../../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../../lib/services/user.settings.service';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {DataType} from '../../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../../lib/datashowbase/column.config';
import {AppSettings} from '../../app.settings';
import {TranslateHelper} from '../../../lib/helper/translate.helper';
import {AppHelper} from '../../../lib/helper/app.helper';
import {InfoLevelType} from '../../../lib/message/info.leve.type';
import {ValueKeyHtmlSelectOptions} from '../../../lib/dynamic-form/models/value.key.html.select.options';
import {BaseSettings} from '../../../lib/base.settings';

import {GlobalparameterGTService} from '../../../gtservice/globalparameter.gt.service';
import {GTNetSecurityImpHead} from '../model/gtnet-security-imp-head';
import {GTNetSecurityImpPos} from '../model/gtnet-security-imp-pos';
import {GTNetSecurityImpPosService} from '../service/gtnet-security-imp-pos.service';
import {SecurityService} from '../../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../../securitycurrency/service/currencypair.service';
import {SecurityCurrencyHelper} from '../../../securitycurrency/service/security.currency.helper';
import {SecurityDataProviderUrls} from '../../../securitycurrency/model/security.data.provider.urls';
import {SecuritycurrencyExtendedInfoComponent} from '../../../watchlist/component/securitycurrency-extended-info.component';
import {SecurityEditComponent} from '../../securitycurrency/security-edit.component';
import {Security} from '../../../entities/security';
import {ProcessedActionData} from '../../../lib/types/processed.action.data';
import {ProcessedAction} from '../../../lib/types/processed.action';
import {FileUploadParam} from '../../../lib/generaldialog/model/file.upload.param';
import {UploadFileDialogComponent} from '../../../lib/generaldialog/component/upload-file-dialog.component';
import {AppHelpIds} from '../../help/help.ids';
import {HelpIds} from '../../../lib/help/help.ids';
import {IGlobalMenuAttach} from '../../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../../lib/mainmenubar/service/active.panel.service';
import {GTNetSecurityImpGapTableComponent} from './gtnet-security-imp-gap-table.component';
import {GTNetService} from '../../../lib/gnet/service/gtnet.service';
import {GTNet} from '../../../lib/gnet/model/gtnet';

/**
 * Table component for displaying and editing GTNet security import positions.
 * Uses EditableTableComponent for inline row editing with per-row persistence.
 */
@Component({
  selector: 'gtnet-security-import-table',
  template: `
    <div #cmDiv class="data-container-inner" (click)="onComponentClick($event)">
      <editable-table #editableTable
        [(data)]="positions"
        [fields]="fields"
        dataKey="idGtNetSecurityImpPos"
        [selectionMode]="'single'"
        [(selection)]="selectedPosition"
        [valueGetterFn]="getValueByPath.bind(this)"
        [baseLocale]="baseLocale"
        [createNewEntityFn]="createNewEntity.bind(this)"
        [contextMenuEnabled]="false"
        [containerClass]="''"
        [expandable]="true"
        [contextMenuAppendTo]="'body'"
        [expandedRowTemplate]="expandedContent"
        [canExpandFn]="canExpandPosition"
        (rowExpand)="onRowExpand($event)"
        (rowEditSave)="onRowEditSave($event)"
        (rowSelect)="onRowSelect($event)"
        (rowUnselect)="onRowUnselect($event)">
      </editable-table>
      @if (selectedHead && contextMenuItems.length > 0) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
    </div>

    <!-- Expanded row content template -->
    <ng-template #expandedContent let-position>
      @if (position.security) {
        <securitycurrency-extended-info
          [securitycurrency]="position.security"
          [feedConnectorsKV]="feedConnectorsKV"
          [intradayUrl]="getUrlForPosition(position, 'intradayUrl')"
          [historicalUrl]="getUrlForPosition(position, 'historicalUrl')"
          [dividendUrl]="getUrlForPosition(position, 'dividendUrl')"
          [splitUrl]="getUrlForPosition(position, 'splitUrl')">
        </securitycurrency-extended-info>
      } @else if (position.gaps?.length > 0) {
        <gtnet-security-imp-gap-table
          [gaps]="position.gaps"
          [gtNetsMap]="gtNetsMap">
        </gtnet-security-imp-gap-table>
      }
    </ng-template>

    <!-- Security Edit Dialog -->
    @if (visibleEditSecurityDialog) {
      <security-edit (closeDialog)="handleCloseEditSecurityDialog($event)"
                     [securityCurrencypairCallParam]="securityCurrencypairCallParam"
                     [visibleEditSecurityDialog]="visibleEditSecurityDialog">
      </security-edit>
    }

    <!-- CSV Upload Dialog -->
    @if (visibleUploadDialog) {
      <upload-file-dialog [visibleDialog]="visibleUploadDialog"
                          [fileUploadParam]="fileUploadParam"
                          (closeDialog)="handleCloseUploadDialog($event)">
      </upload-file-dialog>
    }
  `,
  standalone: true,
  imports: [CommonModule, EditableTableComponent, ContextMenuModule, SecuritycurrencyExtendedInfoComponent, SecurityEditComponent, UploadFileDialogComponent, GTNetSecurityImpGapTableComponent]
})
export class GTNetSecurityImportTableComponent extends TableEditConfigBase implements OnInit, IGlobalMenuAttach {

  @ViewChild('editableTable') editableTable: EditableTableComponent<GTNetSecurityImpPos>;

  @Input() selectedHead: GTNetSecurityImpHead;
  @Output() positionChanged = new EventEmitter<void>();

  positions: GTNetSecurityImpPos[] = [];
  selectedPosition: GTNetSecurityImpPos;
  contextMenuItems: MenuItem[] = [];

  /** Controls visibility of the security edit dialog */
  visibleEditSecurityDialog = false;
  /** Security parameter for the edit dialog */
  securityCurrencypairCallParam: Security;

  /** Controls visibility of the CSV upload dialog */
  visibleUploadDialog = false;
  /** Configuration for the file upload dialog */
  fileUploadParam: FileUploadParam;

  private readonly ENTITY_NAME = 'GTNET_SECURITY_IMP_POS';

  private currencyOptions: ValueKeyHtmlSelectOptions[] = [];
  private newRowCounter = 0;

  /** Map of feed connector IDs to human-readable names */
  feedConnectorsKV: { [id: string]: string } = {};

  /** Map of GTNet IDs to GTNet entities for displaying domain names in gap table */
  gtNetsMap: Map<number, GTNet> = new Map();

  /** Cache of data provider URLs keyed by security ID */
  private dataProviderUrlsCache: { [idSecuritycurrency: number]: SecurityDataProviderUrls } = {};

  constructor(private activePanelService: ActivePanelService,
    private gtNetSecurityImpPosService: GTNetSecurityImpPosService,
    private gtNetService: GTNetService,
    private globalparameterGTService: GlobalparameterGTService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    private securityService: SecurityService,
    private currencypairService: CurrencypairService,
    private cdr: ChangeDetectorRef,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filterService, usersettingsService, translateService, gps);

    // Configure columns
    this.addEditColumnFeqH(DataType.String, 'isin', false, {width: 120});
    const isinCol = this.getColumnConfigByField('isin');
    isinCol.cec.maxLength = 12;

    this.addEditColumnFeqH(DataType.String, 'tickerSymbol', false, {width: 80});
    const tickerCol = this.getColumnConfigByField('tickerSymbol');
    tickerCol.cec.maxLength = 6;

    this.addEditColumnFeqH(DataType.String, 'currency', true, {width: 100});

    // Security name column (read-only, shows linked security if any)
    this.addColumn(DataType.String, 'security.name', 'LINKED_SECURITY', true, true, {width: 200});

    // Asset class columns (read-only, shows linked security's asset class)
    this.addColumn(DataType.String, 'security.assetClass.categoryType', AppSettings.ASSETCLASS.toUpperCase(), true, true,
      {translateValues: TranslateValue.NORMAL, width: 60});
    this.addColumn(DataType.String, 'security.assetClass.specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true, true,
      {translateValues: TranslateValue.NORMAL, width: 80});
    this.addColumn(DataType.String, 'security.assetClass.subCategoryNLS.map.' + gps.getUserLang(),
      'SUB_ASSETCLASS', true, true, {width: 100});
  }

  ngOnInit(): void {
    // Load feed connectors for SecuritycurrencyExtendedInfoComponent
    SecurityCurrencyHelper.loadAllConnectors(
      this.securityService,
      this.currencypairService,
      this.feedConnectorsKV
    );

    // Load GTNets for displaying domain names in gap table
    this.gtNetService.getAllGTNetsWithMessages().subscribe(result => {
      this.gtNetsMap = new Map(result.gtNetList.map(gtNet => [gtNet.idGtNet, gtNet]));
    });

    // Load currency options
    this.globalparameterGTService.getCurrencies().subscribe((currencies: ValueKeyHtmlSelectOptions[]) => {
      this.currencyOptions = currencies;
      const currencyCol = this.getColumnConfigByField('currency');
      currencyCol.cec.valueKeyHtmlOptions = this.currencyOptions;
      this.prepareTableAndTranslate();
    });
  }

  /**
   * Determines if a position row can be expanded.
   * Positions with a linked Security show security details.
   * Positions with gaps (but no security) show gap information.
   */
  canExpandPosition = (position: GTNetSecurityImpPos): boolean => {
    return position.security != null || (position.gaps != null && position.gaps.length > 0);
  };

  /**
   * Handles row expand event to fetch data provider URLs.
   * Stores fetched URLs directly on the position for proper change detection.
   */
  onRowExpand(event: { data: GTNetSecurityImpPos }): void {
    const position = event.data;
    if (position.security?.idSecuritycurrency && !(position as any).dataProviderUrls) {
      this.securityService.getDataProviderUrls(position.security.idSecuritycurrency).subscribe(
        (urls: SecurityDataProviderUrls) => {
          // Store URLs directly on position for change detection
          (position as any).dataProviderUrls = urls;
          // Also cache for potential reuse
          this.dataProviderUrlsCache[position.security.idSecuritycurrency] = urls;
          // Trigger change detection to update the template bindings
          this.cdr.markForCheck();
        }
      );
    }
  }

  /**
   * Gets a specific URL from the position's stored URLs.
   */
  getUrlForPosition(position: GTNetSecurityImpPos, urlType: keyof SecurityDataProviderUrls): string | null {
    const urls = (position as any).dataProviderUrls as SecurityDataProviderUrls;
    return urls ? urls[urlType] : null;
  }

  /**
   * Loads positions for the selected header.
   *
   * @param head the selected header or null to clear
   */
  loadPositions(head: GTNetSecurityImpHead): void {
    this.selectedHead = head;
    this.selectedPosition = null;
    this.resetMenu();

    if (head) {
      this.gtNetSecurityImpPosService.getByHead(head.idGtNetSecurityImpHead).subscribe(
        (positions: GTNetSecurityImpPos[]) => {
          this.positions = positions.map(pos => {
            (pos as any).rowKey = pos.idGtNetSecurityImpPos
              ? `existing_${pos.idGtNetSecurityImpPos}`
              : `new_${this.newRowCounter++}`;
            return pos;
          });
          // Create translated value store for asset class enum columns
          this.createTranslatedValueStore(this.positions);
          this.resetMenu();
          this.positionChanged.emit();
        }
      );
    } else {
      this.positions = [];
      this.positionChanged.emit();
    }
  }

  /**
   * Handles component click to refresh context menu and register with main menu bar.
   */
  onComponentClick(event: any): void {
    this.resetMenu();
  }

  public getHelpContextId(): string {
    return AppHelpIds.HELP_BASEDATA_GT_NET_IMPORT_SECURITY;
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  };

  callMeDeactivate(): void {
  }

  /**
   * Handles adding a new row via the editable table.
   */
  handleAddNewRow(): void {
    if (this.editableTable) {
      this.editableTable.addNewRow();
    }
  }

  /**
   * Creates a new position entity for inline editing.
   */
  createNewEntity = (): GTNetSecurityImpPos => {
    const entity = new GTNetSecurityImpPos();
    entity.idGtNetSecurityImpHead = this.selectedHead?.idGtNetSecurityImpHead;
    (entity as any).rowKey = `new_${this.newRowCounter++}`;
    return entity;
  };

  /**
   * Handles row save events from the editable table.
   */
  onRowEditSave(event: RowEditSaveEvent<GTNetSecurityImpPos>): void {
    const entity = event.row;

    // Validate that at least ISIN or ticker symbol is provided
    if (!entity.isin && !entity.tickerSymbol) {
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'ISIN_OR_TICKER_REQUIRED');
      return;
    }

    // Ensure head reference is set
    entity.idGtNetSecurityImpHead = this.selectedHead.idGtNetSecurityImpHead;

    // Clean up temporary fields before saving
    const entityToSave = new GTNetSecurityImpPos();
    entityToSave.idGtNetSecurityImpHead = entity.idGtNetSecurityImpHead;
    entityToSave.isin = entity.isin;
    entityToSave.tickerSymbol = entity.tickerSymbol;
    entityToSave.currency = entity.currency;

    // Only set ID if it's a valid number (not a temp string like "new_1")
    if (entity.idGtNetSecurityImpPos && typeof entity.idGtNetSecurityImpPos === 'number') {
      entityToSave.idGtNetSecurityImpPos = entity.idGtNetSecurityImpPos;
    }

    this.gtNetSecurityImpPosService.save(entityToSave).subscribe({
      next: (saved: GTNetSecurityImpPos) => {
        // Update the entity with the saved data (including generated ID)
        Object.assign(entity, saved);
        (entity as any).rowKey = `existing_${saved.idGtNetSecurityImpPos}`;
        this.messageToastService.showMessageI18n(
          InfoLevelType.SUCCESS,
          event.isNew ? 'MSG_RECORD_CREATED' : 'MSG_RECORD_SAVED',
          {i18nRecord: 'GTNET_SECURITY_IMP_POS'}
        );
        this.positionChanged.emit();
      },
      error: () => {
        // On error, reopen the row for editing so user can correct the data
        setTimeout(() => {
          if (this.editableTable) {
            this.editableTable.startEditingRow(entity);
          }
        }, 100);
      }
    });
  }

  /**
   * Handles row selection.
   */
  onRowSelect(event: any): void {
    this.resetMenu();
    this.positionChanged.emit();
  }

  /**
   * Handles row unselection.
   */
  onRowUnselect(event: any): void {
    this.resetMenu();
    this.positionChanged.emit();
  }

  /**
   * Prepares menu items for the context menu and parent component's edit menu.
   */
  prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];

    if (this.selectedHead) {
      menuItems.push({
        label: 'CREATE|' + this.ENTITY_NAME + BaseSettings.DIALOG_MENU_SUFFIX,
        icon: 'pi pi-plus',
        command: () => this.handleAddNewRow()
      });
      menuItems.push({
        label: 'UPLOAD_CSV|' + this.ENTITY_NAME + BaseSettings.DIALOG_MENU_SUFFIX,
        icon: 'pi pi-upload',
        command: () => this.handleUploadCSV()
      });
      menuItems.push({
        label: 'DELETE_RECORD|' + this.ENTITY_NAME,
        disabled: !this.selectedPosition || !this.selectedPosition.idGtNetSecurityImpPos,
        command: () => this.handleDeletePosition()
      });
      menuItems.push({separator: true});
      menuItems.push({
        label: 'EDIT|' + AppSettings.SECURITY.toUpperCase() + BaseSettings.DIALOG_MENU_SUFFIX,
        disabled: !this.selectedPosition?.security,
        command: () => this.handleEditSecurity()
      });
      menuItems.push({
        label: 'DELETE_LINKED_SECURITY',
        disabled: !this.selectedPosition?.security,
        command: () => this.handleDeleteLinkedSecurity()
      });
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Opens the security edit dialog for the selected position's linked security.
   */
  handleEditSecurity(): void {
    if (this.selectedPosition?.security) {
      this.securityCurrencypairCallParam = this.selectedPosition.security;
      this.visibleEditSecurityDialog = true;
    }
  }

  /**
   * Handles security edit dialog close event.
   * Reloads positions if the security was updated to reflect any changes.
   */
  handleCloseEditSecurityDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditSecurityDialog = false;
    if (processedActionData.action === ProcessedAction.UPDATED) {
      // Reload positions to get updated security data
      this.loadPositions(this.selectedHead);
    }
  }

  /**
   * Opens the CSV upload dialog for importing positions from file.
   */
  handleUploadCSV(): void {
    this.fileUploadParam = new FileUploadParam(
      AppHelpIds.HELP_BASEDATA_GT_NET_IMPORT_SECURITY,
      null,
      'csv',
      'UPLOAD_CSV_GTNET_SECURITY_IMP_POS',
      false,
      this.gtNetSecurityImpPosService,
      this.selectedHead.idGtNetSecurityImpHead
    );
    this.visibleUploadDialog = true;
  }

  /**
   * Handles upload dialog close event.
   * Reloads positions if upload was successful.
   */
  handleCloseUploadDialog(processedActionData: ProcessedActionData): void {
    this.visibleUploadDialog = false;
    if (processedActionData.action === ProcessedAction.UPDATED) {
      this.loadPositions(this.selectedHead);
    }
  }

  /**
   * Handles position deletion.
   */
  private handleDeletePosition(): void {
    if (!this.selectedPosition?.idGtNetSecurityImpPos) {
      return;
    }

    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|GTNET_SECURITY_IMP_POS',
      () => {
        this.gtNetSecurityImpPosService.deleteEntity(this.selectedPosition.idGtNetSecurityImpPos).subscribe(() => {
          this.messageToastService.showMessageI18n(
            InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD',
            {i18nRecord: 'GTNET_SECURITY_IMP_POS'}
          );
          this.positions = this.positions.filter(p => p !== this.selectedPosition);
          this.selectedPosition = null;
          this.positionChanged.emit();
        });
      }
    );
  }

  /**
   * Handles deletion of the linked security from the selected position.
   * The position remains and can be queried again via GTNet.
   */
  private handleDeleteLinkedSecurity(): void {
    if (!this.selectedPosition?.idGtNetSecurityImpPos || !this.selectedPosition?.security) {
      return;
    }

    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_LINKED_SECURITY',
      () => {
        this.gtNetSecurityImpPosService.deleteLinkedSecurity(this.selectedPosition.idGtNetSecurityImpPos).subscribe(
          (updatedPosition: GTNetSecurityImpPos) => {
            this.messageToastService.showMessageI18n(
              InfoLevelType.SUCCESS,
              'MSG_LINKED_SECURITY_DELETED'
            );
            // Update the position in the list
            const index = this.positions.findIndex(p => p.idGtNetSecurityImpPos === updatedPosition.idGtNetSecurityImpPos);
            if (index >= 0) {
              this.positions[index] = updatedPosition;
              (this.positions[index] as any).rowKey = `existing_${updatedPosition.idGtNetSecurityImpPos}`;
            }
            this.selectedPosition = updatedPosition;
            this.resetMenu();
            this.positionChanged.emit();
          }
        );
      }
    );
  }

  /**
   * Updates the context menu and registers menu items with the main menu bar.
   * This method must be called from onComponentClick, onRowSelect, and onRowUnselect
   * to ensure menu items appear both in the context menu and the application's main menu bar.
   */
  private resetMenu(): void {
    this.contextMenuItems = this.prepareEditMenu();
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems
    });
  }

}

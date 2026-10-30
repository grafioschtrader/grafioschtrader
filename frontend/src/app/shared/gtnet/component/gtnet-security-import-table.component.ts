import { Component, DestroyRef, inject, OnInit, signal, output, viewChild, input, linkedSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';

import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';

import { TableEditConfigBase } from '../../../lib/datashowbase/table.edit.config.base';
import { EditableTableComponent, RowEditSaveEvent } from '../../../lib/datashowbase/editable-table.component';
import { MessageToastService } from '../../../lib/message/message.toast.service';
import { DataType } from '../../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../../lib/datashowbase/column.config';
import { AppSettings } from '../../app.settings';
import { TranslateHelper } from '../../../lib/helper/translate.helper';
import { AppHelper } from '../../../lib/helper/app.helper';
import { InfoLevelType } from '../../../lib/message/info.leve.type';
import { ValueKeyHtmlSelectOptions } from '../../../lib/dynamic-form/models/value.key.html.select.options';
import { BaseSettings } from '../../../lib/base.settings';

import { GlobalparameterGTService } from '../../../gtservice/globalparameter.gt.service';
import { GTNetSecurityImpHead } from '../model/gtnet-security-imp-head';
import { GTNetSecurityImpPos } from '../model/gtnet-security-imp-pos';
import { GTNetSecurityImpPosService } from '../service/gtnet-security-imp-pos.service';
import { SecurityService } from '../../../securitycurrency/service/security.service';
import { CurrencypairService } from '../../../securitycurrency/service/currencypair.service';
import { SecurityCurrencyHelper } from '../../../securitycurrency/service/security.currency.helper';
import { SecurityDataProviderUrls } from '../../../securitycurrency/model/security.data.provider.urls';
import { SecuritycurrencyExtendedInfoComponent } from '../../../watchlist/component/securitycurrency-extended-info.component';
import { SecurityEditComponent } from '../../securitycurrency/security-edit.component';
import { Security } from '../../../entities/security';
import { ProcessedActionData } from '../../../lib/types/processed.action.data';
import { ProcessedAction } from '../../../lib/types/processed.action';
import { FileUploadParam } from '../../../lib/generaldialog/model/file.upload.param';
import { UploadFileDialogComponent } from '../../../lib/generaldialog/component/upload-file-dialog.component';
import { AppHelpIds } from '../../help/help.ids';
import { IGlobalMenuAttach } from '../../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../../lib/mainmenubar/service/active.panel.service';
import { GTNetSecurityImpGapTableComponent } from './gtnet-security-imp-gap-table.component';
import { GTNetService } from '../../../lib/gnet/service/gtnet.service';
import { GTNet } from '../../../lib/gnet/model/gtnet';

/**
 * Table component for displaying and editing GTNet security import positions.
 * Uses EditableTableComponent for inline row editing with per-row persistence.
 */
@Component({
  selector: 'gtnet-security-import-table',
  template: `
    <div #cmDiv class="data-container-inner" (click)="onComponentClick($event)">
      <editable-table
        #editableTable
        [(data)]="positions"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        dataKey="idGtNetSecurityImpPos"
        [selectionMode]="'single'"
        [(selection)]="selectedPosition"
        [valueGetterFn]="valueGetter"
        [baseLocale]="baseLocale"
        [createNewEntityFn]="createNewEntity"
        [contextMenuEnabled]="false"
        [containerClass]="''"
        [expandable]="true"
        [contextMenuAppendTo]="'body'"
        [expandedRowTemplate]="expandedContent"
        [canExpandFn]="canExpandPosition"
        (rowExpand)="onRowExpand($event)"
        (rowEditSave)="onRowEditSave($event)"
        (rowSelect)="onRowSelect($event)"
        (rowUnselect)="onRowUnselect($event)" />
      @if (selectedHead() && contextMenuItems()?.length > 0) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems()" />
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
          [splitUrl]="getUrlForPosition(position, 'splitUrl')" />
      } @else if (position.gaps?.length > 0) {
        <gtnet-security-imp-gap-table [gaps]="position.gaps" [gtNetsMap]="gtNetsMap()" />
      }
    </ng-template>

    <!-- Security Edit Dialog -->
    @if (visibleEditSecurityDialog()) {
      <security-edit
        (closeDialog)="handleCloseEditSecurityDialog($event)"
        [securityCurrencypairCallParam]="securityCurrencypairCallParam"
        [visibleEditSecurityDialog]="visibleEditSecurityDialog()" />
    }

    <!-- CSV Upload Dialog -->
    @if (visibleUploadDialog()) {
      <upload-file-dialog
        [visibleDialog]="visibleUploadDialog()"
        [fileUploadParam]="fileUploadParam"
        (closeDialog)="handleCloseUploadDialog($event)" />
    }
  `,
  imports: [
    EditableTableComponent,
    ContextMenuModule,
    SecuritycurrencyExtendedInfoComponent,
    SecurityEditComponent,
    UploadFileDialogComponent,
    GTNetSecurityImpGapTableComponent
  ]
})
export class GTNetSecurityImportTableComponent extends TableEditConfigBase implements OnInit, IGlobalMenuAttach {
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private gtNetSecurityImpPosService: GTNetSecurityImpPosService = inject(GTNetSecurityImpPosService);
  private gtNetService: GTNetService = inject(GTNetService);
  private globalparameterGTService: GlobalparameterGTService = inject(GlobalparameterGTService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private confirmationService: ConfirmationService = inject(ConfirmationService);
  private securityService: SecurityService = inject(SecurityService);
  private currencypairService: CurrencypairService = inject(CurrencypairService);

  readonly editableTable = viewChild<EditableTableComponent<GTNetSecurityImpPos>>('editableTable');

  readonly selectedHeadInput = input<GTNetSecurityImpHead | null>(undefined, { alias: 'selectedHead' });
  readonly selectedHead = linkedSignal(() => this.selectedHeadInput());
  readonly positionChanged = output<void>();

  readonly positions = signal<GTNetSecurityImpPos[]>([]);
  readonly selectedPosition = signal<GTNetSecurityImpPos | null>(null);
  /** Translated menu of the positions, shown as context menu and in the menu bar */
  readonly contextMenuItems = signal<MenuItem[]>([]);

  /** Controls visibility of the security edit dialog */
  readonly visibleEditSecurityDialog = signal(false);
  /** Security parameter for the edit dialog */
  securityCurrencypairCallParam: Security;

  /** Controls visibility of the CSV upload dialog */
  readonly visibleUploadDialog = signal(false);
  /** Configuration for the file upload dialog */
  fileUploadParam: FileUploadParam;

  private readonly ENTITY_NAME = 'GTNET_SECURITY_IMP_POS';

  private currencyOptions: ValueKeyHtmlSelectOptions[] = [];
  private newRowCounter = 0;

  /** Map of feed connector IDs to human-readable names */
  feedConnectorsKV: { [id: string]: string } = {};

  /** Map of GTNet IDs to GTNet entities for displaying domain names in gap table */
  readonly gtNetsMap = signal<Map<number, GTNet>>(new Map());

  private readonly destroyRef = inject(DestroyRef);
  /** Positions of the latest head; a newer head replaces a request still outstanding */
  private positionsSubscription: Subscription;
  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription: Subscription;

  /** Cache of data provider URLs keyed by security ID */
  private dataProviderUrlsCache: {
    [idSecuritycurrency: number]: SecurityDataProviderUrls;
  } = {};

  constructor() {
    super();

    // Configure columns
    this.addEditColumnFeqH(DataType.String, 'isin', false, { width: 120 });
    const isinCol = this.requireEditColumnByField('isin');
    isinCol.cec.maxLength = 12;

    this.addEditColumnFeqH(DataType.String, 'tickerSymbol', false, {
      width: 80
    });
    const tickerCol = this.requireEditColumnByField('tickerSymbol');
    tickerCol.cec.maxLength = this.gps.getFieldSize(AppSettings.FIELD_SIZE_MAX_Security_Ticker_Symbol);

    this.addEditColumnFeqH(DataType.String, 'currency', true, { width: 100 });

    // Security name column (read-only, shows linked security if any)
    this.addColumn(DataType.String, 'security.name', 'LINKED_SECURITY', true, true, { width: 200 });

    // Asset class columns (read-only, shows linked security's asset class)
    this.addColumn(
      DataType.String,
      'security.assetClass.categoryType',
      AppSettings.ASSETCLASS.toUpperCase(),
      true,
      true,
      { translateValues: TranslateValue.NORMAL, width: 60 }
    );
    this.addColumn(
      DataType.String,
      'security.assetClass.specialInvestmentInstrument',
      'FINANCIAL_INSTRUMENT',
      true,
      true,
      { translateValues: TranslateValue.NORMAL, width: 80 }
    );
    this.addColumn(
      DataType.String,
      'security.assetClass.subCategoryNLS.map.' + this.gps.getUserLang(),
      'SUB_ASSETCLASS',
      true,
      true,
      { width: 100 }
    );
  }

  ngOnInit(): void {
    // Load feed connectors for SecuritycurrencyExtendedInfoComponent
    SecurityCurrencyHelper.loadAllConnectors(
      this.securityService,
      this.currencypairService,
      this.feedConnectorsKV,
      () => this.notifyViewChanged()
    );
    this.destroyRef.onDestroy(() => this.menuSubscription?.unsubscribe());

    // Load GTNets for displaying domain names in gap table
    this.gtNetService
      .getAllGTNetsWithMessages()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.gtNetsMap.set(new Map(result.gtNetList.map((gtNet) => [gtNet.idGtNet, gtNet]))));

    // Load currency options
    this.globalparameterGTService.getCurrencies().subscribe((currencies: ValueKeyHtmlSelectOptions[]) => {
      this.currencyOptions = currencies;
      const currencyCol = this.requireEditColumnByField('currency');
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
      this.securityService
        .getDataProviderUrls(position.security.idSecuritycurrency)
        .subscribe((urls: SecurityDataProviderUrls) => {
          // Store URLs directly on position for change detection
          (position as any).dataProviderUrls = urls;
          // Also cache for potential reuse
          this.dataProviderUrlsCache[position.security.idSecuritycurrency] = urls;
          // The expanded row reads the URLs from the position
          this.notifyViewChanged();
        });
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
  loadPositions(head: GTNetSecurityImpHead | null | undefined): void {
    this.selectedHead.set(head);
    this.selectedPosition.set(null);
    this.resetMenu();
    this.positionsSubscription?.unsubscribe();

    if (head) {
      this.positionsSubscription = this.gtNetSecurityImpPosService
        .getByHead(head.idGtNetSecurityImpHead)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((positions: GTNetSecurityImpPos[]) => {
          const rows = positions.map((pos) => {
            (pos as any).rowKey = pos.idGtNetSecurityImpPos
              ? `existing_${pos.idGtNetSecurityImpPos}`
              : `new_${this.newRowCounter++}`;
            return pos;
          });
          // Create translated value store for asset class enum columns
          this.createTranslatedValueStore(rows);
          this.positions.set(rows);
          this.resetMenu();
          this.positionChanged.emit();
        });
    } else {
      this.positions.set([]);
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

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  /**
   * Handles adding a new row via the editable table.
   */
  handleAddNewRow(): void {
    const editableTable = this.editableTable();
    if (editableTable) {
      editableTable.addNewRow();
    }
  }

  /**
   * Creates a new position entity for inline editing.
   */
  createNewEntity = (): GTNetSecurityImpPos => {
    const entity = new GTNetSecurityImpPos();
    entity.idGtNetSecurityImpHead = this.selectedHead()?.idGtNetSecurityImpHead;
    (entity as any).rowKey = `new_${this.newRowCounter++}`;
    return entity;
  };

  /**
   * Handles row save events from the editable table.
   */
  onRowEditSave(event: RowEditSaveEvent<GTNetSecurityImpPos>): void {
    const headId = this.selectedHead()?.idGtNetSecurityImpHead;
    if (headId == null) return;
    const entity = event.row;

    // Validate that at least ISIN or ticker symbol is provided
    if (!entity.isin && !entity.tickerSymbol) {
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'ISIN_OR_TICKER_REQUIRED');
      return;
    }

    // Ensure head reference is set
    entity.idGtNetSecurityImpHead = headId;

    // Clean up temporary fields before saving
    const entityToSave = new GTNetSecurityImpPos();
    entityToSave.idGtNetSecurityImpHead = entity.idGtNetSecurityImpHead;
    entityToSave.isin = entity.isin;
    entityToSave.tickerSymbol = entity.tickerSymbol;
    entityToSave.currency = entity.currency;

    // Only set ID if it is a persisted one. A temporary key must never travel: save() branches on the id to choose
    // between POST and PUT, and an update of a row that does not exist is answered with a security breach, which
    // counts against the user's g.max.security.breach.count. Two kinds of temporary key occur here - the string
    // `new_<n>` this component puts into rowKey, and the negative number EditableTableComponent.addNewRow() assigns
    // as the dataKey of a row whose key is still null - so a positive number is the only thing that may be copied.
    if (typeof entity.idGtNetSecurityImpPos === 'number' && entity.idGtNetSecurityImpPos > 0) {
      entityToSave.idGtNetSecurityImpPos = entity.idGtNetSecurityImpPos;
    }

    this.gtNetSecurityImpPosService.save(entityToSave).subscribe({
      next: (saved: GTNetSecurityImpPos) => {
        // A newly persisted row changes from a temporary negative dataKey to its generated positive ID. Replacing
        // the row gives Optimus a clean editor context for the next append; mutating that key in place can make the
        // following row's controls retain bindings from the saved row.
        const savedEntity = Object.assign(new GTNetSecurityImpPos(), saved);
        (savedEntity as any).rowKey = `existing_${saved.idGtNetSecurityImpPos}`;
        this.positions.update((positions) =>
          positions.map((position) => (position === entity ? savedEntity : position))
        );
        if (this.selectedPosition() === entity) {
          this.selectedPosition.set(savedEntity);
        }
        this.messageToastService.showMessageI18n(
          InfoLevelType.SUCCESS,
          event.isNew ? 'MSG_RECORD_CREATED' : 'MSG_RECORD_SAVED',
          { i18nRecord: 'GTNET_SECURITY_IMP_POS' }
        );
        this.positionChanged.emit();
      },
      error: () => {
        // On error, reopen the row for editing so user can correct the data
        setTimeout(() => {
          const editableTable = this.editableTable();
          if (editableTable) {
            editableTable.startEditingRow(entity);
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

    if (this.selectedHead()) {
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
        disabled: !this.selectedPosition()?.idGtNetSecurityImpPos,
        command: () => this.handleDeletePosition()
      });
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'EDIT|' + AppSettings.SECURITY.toUpperCase() + BaseSettings.DIALOG_MENU_SUFFIX,
        disabled: !this.selectedPosition()?.security,
        command: () => this.handleEditSecurity()
      });
      menuItems.push({
        label: 'DELETE_LINKED_SECURITY',
        disabled: !this.selectedPosition()?.security,
        command: () => this.handleDeleteLinkedSecurity()
      });
    }

    return menuItems;
  }

  /**
   * Opens the security edit dialog for the selected position's linked security.
   */
  handleEditSecurity(): void {
    const security = this.selectedPosition()?.security;
    if (security) {
      this.securityCurrencypairCallParam = security;
      this.visibleEditSecurityDialog.set(true);
    }
  }

  /**
   * Handles security edit dialog close event.
   * Reloads positions if the security was updated to reflect any changes.
   */
  handleCloseEditSecurityDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditSecurityDialog.set(false);
    if (processedActionData.action === ProcessedAction.UPDATED) {
      // Reload positions to get updated security data
      this.loadPositions(this.selectedHead());
    }
  }

  /**
   * Opens the CSV upload dialog for importing positions from file.
   */
  handleUploadCSV(): void {
    const headId = this.selectedHead()?.idGtNetSecurityImpHead;
    if (headId == null) return;
    this.fileUploadParam = new FileUploadParam(
      AppHelpIds.HELP_BASEDATA_GT_NET_IMPORT_SECURITY,
      null,
      'csv',
      'UPLOAD_CSV_GTNET_SECURITY_IMP_POS',
      false,
      this.gtNetSecurityImpPosService,
      headId
    );
    this.visibleUploadDialog.set(true);
  }

  /**
   * Handles upload dialog close event.
   * Reloads positions if upload was successful.
   */
  handleCloseUploadDialog(processedActionData: ProcessedActionData): void {
    this.visibleUploadDialog.set(false);
    if (processedActionData.action === ProcessedAction.UPDATED) {
      this.loadPositions(this.selectedHead());
    }
  }

  /**
   * Handles position deletion.
   */
  private handleDeletePosition(): void {
    const selected = this.selectedPosition();
    if (!selected?.idGtNetSecurityImpPos) {
      return;
    }

    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|GTNET_SECURITY_IMP_POS',
      () => {
        this.gtNetSecurityImpPosService.deleteEntity(selected.idGtNetSecurityImpPos).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
            i18nRecord: 'GTNET_SECURITY_IMP_POS'
          });
          this.positions.update((positions) => positions.filter((p) => p !== selected));
          this.selectedPosition.set(null);
          this.resetMenu();
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
    const selected = this.selectedPosition();
    if (!selected?.idGtNetSecurityImpPos || !selected?.security) {
      return;
    }

    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_LINKED_SECURITY',
      () => {
        this.gtNetSecurityImpPosService
          .deleteLinkedSecurity(selected.idGtNetSecurityImpPos)
          .subscribe((updatedPosition: GTNetSecurityImpPos) => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_LINKED_SECURITY_DELETED');
            // Replace the position in the list
            (updatedPosition as any).rowKey = `existing_${updatedPosition.idGtNetSecurityImpPos}`;
            this.positions.update((positions) =>
              positions.map((p) =>
                p.idGtNetSecurityImpPos === updatedPosition.idGtNetSecurityImpPos ? updatedPosition : p
              )
            );
            this.selectedPosition.set(updatedPosition);
            this.resetMenu();
            this.positionChanged.emit();
          });
      }
    );
  }

  /**
   * Updates the context menu and registers menu items with the main menu bar.
   * This method must be called from onComponentClick, onRowSelect, and onRowUnselect
   * to ensure menu items appear both in the context menu and the application's main menu bar.
   */
  /** Publishes the translated menu of the positions as context menu and in the menu bar. */
  private resetMenu(): void {
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = TranslateHelper.translateMenuItems$(
      this.prepareEditMenu(),
      this.translateService
    ).subscribe((menuItems) => {
      this.contextMenuItems.set(menuItems);
      this.activePanelService.activatePanel(this, { showMenu: null, editMenu: menuItems });
    });
  }
}

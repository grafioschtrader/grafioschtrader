import {Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {ContextMenuModule} from 'primeng/contextmenu';

import {TableEditConfigBase} from '../../lib/datashowbase/table.edit.config.base';
import {EditableTableComponent, RowEditSaveEvent} from '../../lib/datashowbase/editable-table.component';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppHelper} from '../../lib/helper/app.helper';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {BaseSettings} from '../../lib/base.settings';

import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {GTNetSecurityImpHead} from '../model/gtnet-security-imp-head';
import {GTNetSecurityImpPos} from '../model/gtnet-security-imp-pos';
import {GTNetSecurityImpPosService} from '../service/gtnet-security-imp-pos.service';

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
        (rowEditSave)="onRowEditSave($event)"
        (rowSelect)="onRowSelect($event)"
        (rowUnselect)="onRowUnselect($event)">
      </editable-table>
      @if (selectedHead && contextMenuItems.length > 0) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
    </div>
  `,
  standalone: true,
  imports: [CommonModule, EditableTableComponent, ContextMenuModule]
})
export class GTNetSecurityImportTableComponent extends TableEditConfigBase implements OnInit {

  @ViewChild('editableTable') editableTable: EditableTableComponent<GTNetSecurityImpPos>;

  @Input() selectedHead: GTNetSecurityImpHead;
  @Output() positionChanged = new EventEmitter<void>();

  positions: GTNetSecurityImpPos[] = [];
  selectedPosition: GTNetSecurityImpPos;
  contextMenuItems: MenuItem[] = [];

  private readonly ENTITY_NAME = 'GTNET_SECURITY_IMP_POS';

  private currencyOptions: ValueKeyHtmlSelectOptions[] = [];
  private newRowCounter = 0;

  constructor(
    private gtNetSecurityImpPosService: GTNetSecurityImpPosService,
    private globalparameterGTService: GlobalparameterGTService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
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
  }

  ngOnInit(): void {
    // Load currency options
    this.globalparameterGTService.getCurrencies().subscribe((currencies: ValueKeyHtmlSelectOptions[]) => {
      this.currencyOptions = currencies;
      const currencyCol = this.getColumnConfigByField('currency');
      currencyCol.cec.valueKeyHtmlOptions = this.currencyOptions;
      this.prepareTableAndTranslate();
    });
  }

  /**
   * Loads positions for the selected header.
   *
   * @param head the selected header or null to clear
   */
  loadPositions(head: GTNetSecurityImpHead): void {
    this.selectedHead = head;
    this.selectedPosition = null;
    this.updateContextMenu();

    if (head) {
      this.gtNetSecurityImpPosService.getByHead(head.idGtNetSecurityImpHead).subscribe(
        (positions: GTNetSecurityImpPos[]) => {
          this.positions = positions.map(pos => {
            (pos as any).rowKey = pos.idGtNetSecurityImpPos
              ? `existing_${pos.idGtNetSecurityImpPos}`
              : `new_${this.newRowCounter++}`;
            return pos;
          });
          this.updateContextMenu();
          this.positionChanged.emit();
        }
      );
    } else {
      this.positions = [];
      this.positionChanged.emit();
    }
  }

  /**
   * Handles component click to refresh context menu.
   */
  onComponentClick(event: any): void {
    this.updateContextMenu();
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
    this.updateContextMenu();
    this.positionChanged.emit();
  }

  /**
   * Handles row unselection.
   */
  onRowUnselect(event: any): void {
    this.updateContextMenu();
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
        label: 'DELETE_RECORD|' + this.ENTITY_NAME,
        disabled: !this.selectedPosition || !this.selectedPosition.idGtNetSecurityImpPos,
        command: () => this.handleDeletePosition()
      });
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
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
   * Updates the context menu based on current selection state.
   */
  private updateContextMenu(): void {
    this.contextMenuItems = this.prepareEditMenu();
  }
}

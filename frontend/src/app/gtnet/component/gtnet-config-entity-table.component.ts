import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {GTNetConfigEntity, GTNetEntity, GTNetExchangeKindType, SupplierConsumerLogTypes} from '../model/gtnet';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {FilterService, MenuItem} from 'primeng/api';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {GTNetConfigEntityEditComponent} from './gtnet-config-entity-edit.component';

/**
 * Table component for displaying GTNetConfigEntity records within the expandedRow of GTNetSetupTableComponent.
 * Shows entity-specific exchange configuration (exchange status, logging, consumer priority).
 * Only displayed when GTNetConfigEntity exists for a GTNetEntity.
 * Provides context menu for editing supplierLog, consumerLog, and consumerUsage fields.
 */
@Component({
  selector: 'gtnet-config-entity-table',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    ConfigurableTableComponent,
    ContextMenuModule,
    TooltipModule,
    GTNetConfigEntityEditComponent
  ],
  template: `
    <div class="nested-table-container">
      <h5 class="nested-table-header">{{ 'GT_NET_CONFIG_ENTITY_TABLE' | translate }}</h5>
      <configurable-table
        [data]="configEntities"
        [fields]="fields"
        [dataKey]="'idGtNetEntity'"
        [(selection)]="selectedEntity"
        [contextMenuItems]="contextMenuItems"
        [contextMenuAppendTo]="'body'"
        [showContextMenu]="true"
        [containerClass]="{'data-container-full': true, 'nested-table': true}"
        [valueGetterFn]="getValueByPath.bind(this)"
        (selectionChange)="onSelectionChange($event)">
      </configurable-table>
    </div>

    @if (visibleDialog) {
      <gtnet-config-entity-edit
        [visibleDialog]="visibleDialog"
        [gtNetConfigEntity]="selectedEntity"
        (closeDialog)="handleCloseDialog($event)">
      </gtnet-config-entity-edit>
    }
  `,
  styles: [`
    .nested-table-container {
      margin-bottom: 1rem;
      padding: 0.5rem;
      background-color: rgba(0, 0, 0, 0.02);
      border-radius: 4px;
    }
    .nested-table-header {
      margin: 0 0 0.5rem 0;
      padding: 0;
      font-size: 0.9rem;
      font-weight: 600;
    }
  `]
})
export class GTNetConfigEntityTableComponent extends TableConfigBase implements OnInit {
  @Input() gtNetEntities: GTNetEntity[];
  @Output() dataChanged = new EventEmitter<ProcessedActionData>();

  configEntities: GTNetConfigEntityDisplay[] = [];
  selectedEntity: GTNetConfigEntityDisplay = null;
  contextMenuItems: MenuItem[] = [];
  visibleDialog = false;

  constructor(
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService
  ) {
    super(filterService, usersettingsService, translateService, gps);
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'entityKind', 'ENTITY', true, false,
      {translateValues: TranslateValue.NORMAL, width: 150});
    this.addColumn(DataType.NumericInteger, 'maxLimit', 'GT_NET_MAX_LIMIT', true, false);
    this.addColumnFeqH(DataType.Boolean, 'exchange',  true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.String, 'supplierLog', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'consumerLog', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.NumericInteger, 'consumerUsage',  true, false);

    this.prepareData();
    this.createTranslatedValueStore(this.configEntities);
    this.translateHeadersAndColumns();
    this.updateContextMenu();
  }

  private prepareData(): void {
    this.configEntities = this.gtNetEntities
      .filter(entity => entity.gtNetConfigEntity != null)
      .map(entity => this.createDisplayEntity(entity));
  }

  private createDisplayEntity(gtNetEntity: GTNetEntity): GTNetConfigEntityDisplay {
    const configEntity = gtNetEntity.gtNetConfigEntity;

    return {
      ...configEntity,
      entityKind: gtNetEntity.entityKind,
      maxLimit: gtNetEntity.maxLimit
    };
  }

  onSelectionChange(entity: GTNetConfigEntityDisplay): void {
    this.selectedEntity = entity;
    this.updateContextMenu();
  }

  private updateContextMenu(): void {
    this.contextMenuItems = [];
    if (this.selectedEntity) {
      this.contextMenuItems.push({
        label: 'EDIT_RECORD|GT_NET_CONFIG_ENTITY_EDIT',
        command: () => this.openEditDialog()
      });
    }
    TranslateHelper.translateMenuItems(this.contextMenuItems, this.translateService);
  }

  private openEditDialog(): void {
    if (this.selectedEntity) {
      this.visibleDialog = true;
    }
  }

  handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.dataChanged.emit(processedActionData);
    }
  }
}

/**
 * Extended interface for display purposes, combining GTNetConfigEntity with entity kind information.
 */
export interface GTNetConfigEntityDisplay {
  idGtNetEntity: number;
  exchange: boolean;
  supplierLog: SupplierConsumerLogTypes | string;
  consumerLog: SupplierConsumerLogTypes | string;
  consumerUsage: number;
  entityKind: GTNetExchangeKindType | string;
  maxLimit?: number;
}


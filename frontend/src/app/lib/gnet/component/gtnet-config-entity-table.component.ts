import { Component, DestroyRef, EventEmitter, inject, Injector, Input, OnInit, Output, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { TableConfigBase } from '../../datashowbase/table.config.base';
import { ExchangeKindTypeInfo, GTNetConfigEntity, GTNetEntity, SupplierConsumerLogTypes } from '../model/gtnet';
import { DataType } from '../../dynamic-form/models/data.type';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { UserSettingsService } from '../../services/user.settings.service';
import { FilterService, MenuItem } from '@openng/optimus-ui/api';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { TranslateHelper } from '../../helper/translate.helper';
import { GTNetConfigEntityEditComponent } from './gtnet-config-entity-edit.component';
import { BaseSettings } from '../../base.settings';

/**
 * Table component for displaying GTNetConfigEntity records within the expandedRow of GTNetSetupTableComponent.
 * Shows entity-specific exchange configuration (exchange status, logging, consumer priority).
 * Only displayed when GTNetConfigEntity exists for a GTNetEntity.
 * Provides context menu for editing supplierLog, consumerLog, and consumerUsage fields.
 */
@Component({
  selector: 'gtnet-config-entity-table',
  imports: [
    TranslateModule,
    ConfigurableTableComponent,
    ContextMenuModule,
    TooltipModule,
    GTNetConfigEntityEditComponent
  ],
  template: `
    <div class="nested-table-container">
      <h5 class="nested-table-header">
        {{ 'GT_NET_CONFIG_ENTITY_TABLE' | translate }}
      </h5>
      <configurable-table
        [data]="configEntities()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        [dataKey]="'idGtNetEntity'"
        [selection]="selectedEntity()"
        [contextMenuItems]="contextMenuItems()"
        [contextMenuAppendTo]="'body'"
        [showContextMenu]="canAdministerGTNet"
        [containerClass]="{ 'data-container-full': true, 'nested-table': true }"
        [valueGetterFn]="valueGetter"
        (selectionChange)="onSelectionChange($event)" />
    </div>

    @if (visibleDialog()) {
      <gtnet-config-entity-edit
        [visibleDialog]="visibleDialog()"
        [gtNetConfigEntity]="selectedEntity()"
        (closeDialog)="handleCloseDialog($event)" />
    }
  `,
  styles: [
    `
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
    `
  ]
})
export class GTNetConfigEntityTableComponent extends TableConfigBase implements OnInit {
  @Input() gtNetEntities: GTNetEntity[];
  @Input() exchangeKindTypes: ExchangeKindTypeInfo[] = [];
  @Output() dataChanged = new EventEmitter<ProcessedActionData>();

  readonly configEntities = signal<GTNetConfigEntityDisplay[]>([]);
  readonly selectedEntity = signal<GTNetConfigEntityDisplay>(null);
  /** Translated context menu of the selected row */
  readonly contextMenuItems = signal<MenuItem[]>([]);
  readonly visibleDialog = signal(false);
  protected readonly canAdministerGTNet: boolean;

  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription: Subscription;

  constructor(
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.canAdministerGTNet = gps.hasRole(BaseSettings.ROLE_ADMIN);
    inject(DestroyRef).onDestroy(() => this.menuSubscription?.unsubscribe());
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'entityKind', 'ENTITY', true, false, {
      translateValues: TranslateValue.NORMAL,
      width: 150
    });
    this.addColumn(DataType.NumericInteger, 'maxLimit', 'GT_NET_MAX_LIMIT', true, false);
    this.addColumnFeqH(DataType.Boolean, 'exchange', true, false, {
      templateName: 'check'
    });
    this.addColumnFeqH(DataType.String, 'supplierLog', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.String, 'consumerLog', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.NumericInteger, 'consumerUsage', true, false);

    const configEntities = this.prepareData();
    this.createTranslatedValueStore(configEntities);
    this.configEntities.set(configEntities);
    this.translateHeadersAndColumns();
    this.updateContextMenu();
  }

  private prepareData(): GTNetConfigEntityDisplay[] {
    return this.gtNetEntities
      .filter((entity) => entity.gtNetConfigEntity != null)
      .map((entity) => this.createDisplayEntity(entity));
  }

  private createDisplayEntity(gtNetEntity: GTNetEntity): GTNetConfigEntityDisplay {
    const configEntity = gtNetEntity.gtNetConfigEntity;

    // Convert numeric enum values to string names for translation
    const kindValue =
      typeof gtNetEntity.entityKind === 'number' ? gtNetEntity.entityKind : Number(gtNetEntity.entityKind);
    const entityKindName = Number.isNaN(kindValue)
      ? String(gtNetEntity.entityKind)
      : (this.exchangeKindTypes.find((kind) => kind.value === kindValue)?.name ?? String(kindValue));

    const supplierLogName =
      typeof configEntity.supplierLog === 'number'
        ? SupplierConsumerLogTypes[configEntity.supplierLog]
        : configEntity.supplierLog;

    const consumerLogName =
      typeof configEntity.consumerLog === 'number'
        ? SupplierConsumerLogTypes[configEntity.consumerLog]
        : configEntity.consumerLog;

    return {
      ...configEntity,
      entityKind: entityKindName,
      supplierLog: supplierLogName,
      consumerLog: consumerLogName,
      maxLimit: gtNetEntity.maxLimit
    };
  }

  onSelectionChange(entity: GTNetConfigEntityDisplay | GTNetConfigEntityDisplay[]): void {
    this.selectedEntity.set((Array.isArray(entity) ? entity[0] : entity) ?? null);
    this.updateContextMenu();
  }

  private updateContextMenu(): void {
    const menuItems: MenuItem[] = [];
    if (this.canAdministerGTNet && this.selectedEntity()) {
      menuItems.push({
        label: 'EDIT_RECORD|GT_NET_CONFIG_ENTITY_EDIT',
        command: () => this.openEditDialog()
      });
    }
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = TranslateHelper.translateMenuItems$(menuItems, this.translateService).subscribe(
      (translated) => this.contextMenuItems.set(translated)
    );
  }

  private openEditDialog(): void {
    if (this.canAdministerGTNet && this.selectedEntity()) {
      this.visibleDialog.set(true);
    }
  }

  handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog.set(false);
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
  entityKind: string;
  maxLimit?: number;
}

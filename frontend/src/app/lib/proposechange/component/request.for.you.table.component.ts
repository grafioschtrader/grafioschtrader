import {Component, OnInit} from '@angular/core';
import {UserSettingsService} from '../../../shared/service/user.settings.service';
import {DataType} from '../../../dynamic-form/models/data.type';
import {TranslateService} from '@ngx-translate/core';
import {ActivePanelService} from '../../../shared/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../../shared/service/globalparameter.service';
import {TableConfigBase} from '../../datashowbase/table.config.base';
import {IGlobalMenuAttach} from '../../../shared/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../../shared/help/help.ids';
import {Assetclass} from '../../../entities/assetclass';
import {Stockexchange} from '../../../entities/stockexchange';
import {AssetclassService} from '../../../assetclass/service/assetclass.service';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {AssetclassPrepareEdit} from './assetclass.prepare.edit';
import {GeneralEntityPrepareEdit} from './general.entity.prepare.edit';
import {ProposeChangeEntityWithEntity} from '../model/propose.change.entity.whit.entity';
import {ProposeChangeEntityService} from '../service/propose.change.entity.service';
import {ProposeDataChangeState} from '../../types/propose.data.change.state';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {ImportTransactionPlatform} from '../../../entities/import.transaction.platform';
import {ImportTransactionPlatformPrepareEdit} from './import.transaction.platform.prepare.edit';
import {ImportTransactionPlatformService} from '../../../imptranstemplate/service/import.transaction.platform.service';
import {Security} from '../../../entities/security';
import {TradingPlatformPlan} from '../../../entities/tradingplatformplan';
import {Currencypair} from '../../../entities/currencypair';
import {Historyquote} from '../../../entities/historyquote';
import {ProposeTransientTransfer} from '../../entities/propose.transient.transfer';
import {HistoryquotePrepareEdit} from './historyquote.prepare.edit';
import {SecurityService} from '../../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../../securitycurrency/service/currencypair.service';
import {TranslateHelper} from '../../helper/translate.helper';
import {StockexchangeService} from '../../../stockexchange/service/stockexchange.service';
import {StockexchangePrepareEdit} from './stockexchange.prepare.edit';
import {FilterService, MenuItem} from 'primeng/api';
import {SecurityPrepareEdit} from './security.prepare.edit';
import {ImportTransactionTemplate} from '../../../entities/import.transaction.template';
import {ProposeChangeEntity} from '../../entities/propose.change.entity';
import {TranslateValue} from '../../datashowbase/column.config';
import {AppSettings} from '../../../shared/app.settings';

/**
 * Shows the requested changes on entities in a table.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table [columns]="fields" [value]="proposeChangeEntityWithEntityList" selectionMode="single"
               [(selection)]="selectedEntity"
               dataKey="proposeChangeEntity.idProposeRequest"
               stripedRows showGridlines>
        <ng-template #caption>
          <h4>{{'PROPOSE_CHANGE_ENTITY_FOR_USER' | translate}} {{gps.getIdUser()}}</h4>
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            @for (field of fields; track field) {
              <td>
                {{getValueByPath(el, field)}}
              </td>
            }
          </tr>
        </ng-template>
      </p-table>
      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
    </div>

    @if (entityMappingArr[ASSETCLASS].visibleDialog) {
      <assetclass-edit [visibleDialog]="entityMappingArr[ASSETCLASS].visibleDialog"
                       [callParam]="entityMappingArr[ASSETCLASS].callParam"
                       [proposeChangeEntityWithEntity]="selectedEntity"
                       (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </assetclass-edit>
    }

    @if (entityMappingArr[STOCKEXCHANGE].visibleDialog) {
      <stockexchange-edit [visibleDialog]="entityMappingArr[STOCKEXCHANGE].visibleDialog"
                          [callParam]="entityMappingArr[STOCKEXCHANGE].callParam"
                          [proposeChangeEntityWithEntity]="selectedEntity"
                          (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </stockexchange-edit>
    }
    @if (entityMappingArr[IMPORT_TRANSACTION_PLATFORM].visibleDialog) {
      <import-transaction-edit-platform [visibleDialog]="entityMappingArr[IMPORT_TRANSACTION_PLATFORM].visibleDialog"
                                        [callParam]="entityMappingArr[IMPORT_TRANSACTION_PLATFORM].callParam"
                                        [platformTransactionImportHtmlOptions]="entityMappingArr[IMPORT_TRANSACTION_PLATFORM].option"
                                        [proposeChangeEntityWithEntity]="selectedEntity"
                                        (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </import-transaction-edit-platform>
    }
    @if (entityMappingArr[IMPORT_TRANSACTION_TEMPLATE].visibleDialog) {
      <import-transaction-edit-template [visibleDialog]="entityMappingArr[IMPORT_TRANSACTION_TEMPLATE].visibleDialog"
                                        [callParam]="entityMappingArr[IMPORT_TRANSACTION_TEMPLATE].callParam"
                                        [proposeChangeEntityWithEntity]="selectedEntity"
                                        (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </import-transaction-edit-template>
    }
    @if (entityMappingArr[CURRENCYPAIR].visibleDialog) {
      <currencypair-edit [visibleEditCurrencypairDialog]="entityMappingArr[CURRENCYPAIR].visibleDialog"
                         [securityCurrencypairCallParam]="entityMappingArr[CURRENCYPAIR].callParam"
                         [proposeChangeEntityWithEntity]="selectedEntity"
                         (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </currencypair-edit>
    }

    @if (entityMappingArr[SECURITY].visibleDialog) {
      <security-edit [visibleEditSecurityDialog]="entityMappingArr[SECURITY].visibleDialog"
                     [securityCurrencypairCallParam]="entityMappingArr[SECURITY].callParam"
                     [proposeChangeEntityWithEntity]="selectedEntity"
                     (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </security-edit>
    }
    @if (entityMappingArr[SECURITY_DERIVED].visibleDialog) {
      <security-derived-edit [visibleDialog]="entityMappingArr[SECURITY_DERIVED].visibleDialog"
                             [securityCallParam]="entityMappingArr[SECURITY_DERIVED].callParam"
                             [proposeChangeEntityWithEntity]="selectedEntity"
                             (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </security-derived-edit>
    }
    @if (entityMappingArr[TRADING_PLATFORM_PLAN].visibleDialog) {
      <trading-platform-plan-edit [visibleDialog]="entityMappingArr[TRADING_PLATFORM_PLAN].visibleDialog"
                                  [callParam]="entityMappingArr[TRADING_PLATFORM_PLAN].callParam"
                                  [proposeChangeEntityWithEntity]="selectedEntity"
                                  (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </trading-platform-plan-edit>
    }
    @if (entityMappingArr[HISTORYQUOTE].visibleDialog) {
      <historyquote-edit [visibleDialog]="entityMappingArr[HISTORYQUOTE].visibleDialog"
                         [callParam]="entityMappingArr[HISTORYQUOTE].callParam"
                         [proposeChangeEntityWithEntity]="selectedEntity"
                         (closeDialog)="handleCloseDialog(selectedEntity, $event)">
      </historyquote-edit>
    }
  `,
    standalone: false
})
export class RequestForYouTableComponent extends TableConfigBase implements OnInit, IGlobalMenuAttach {

  readonly ASSETCLASS = AppSettings.ASSETCLASS;
  readonly SECURITY = AppSettings.SECURITY;
  readonly STOCKEXCHANGE = AppSettings.STOCKEXCHANGE;
  readonly IMPORT_TRANSACTION_PLATFORM = AppSettings.IMPORT_TRANSACTION_PLATFORM;
  readonly IMPORT_TRANSACTION_TEMPLATE = AppSettings.IMPORT_TRANSACTION_TEMPLATE;
  readonly CURRENCYPAIR = AppSettings.CURRENCYPAIR;
  readonly SECURITY_DERIVED = 'SecurityDerived';
  readonly TRADING_PLATFORM_PLAN = AppSettings.TRADING_PLATFORM_PLAN;
  readonly HISTORYQUOTE = AppSettings.HISTORYQUOTE;

  contextMenuItems: MenuItem[] = [];

  entityMappingArr: { [key: string]: EntityMapping } = {};

  proposeChangeEntityWithEntityList: ProposeChangeEntityWithEntity[];
  selectedEntity: ProposeChangeEntityWithEntity;

  constructor(private assetclassService: AssetclassService,
              private stockexchangeService: StockexchangeService,
              private importTransactionPlatformService: ImportTransactionPlatformService,
              private securityService: SecurityService,
              private currencypairService: CurrencypairService,
              private proposeChangeEntityService: ProposeChangeEntityService,
              private activePanelService: ActivePanelService,
              private messageToastService: MessageToastService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);

    this.entityMappingArr[this.ASSETCLASS] = new EntityMapping(new AssetclassPrepareEdit(assetclassService));
    this.entityMappingArr[this.STOCKEXCHANGE] = new EntityMapping(new StockexchangePrepareEdit(stockexchangeService,
      this.gps));
    this.entityMappingArr[this.IMPORT_TRANSACTION_PLATFORM] =
      new EntityMapping(new ImportTransactionPlatformPrepareEdit(importTransactionPlatformService));
    this.entityMappingArr[this.IMPORT_TRANSACTION_TEMPLATE] =
      new EntityMapping(new ImportTransactionPlatformPrepareEdit(importTransactionPlatformService));
    this.entityMappingArr[this.CURRENCYPAIR] = new EntityMapping(new GeneralEntityPrepareEdit(Currencypair));
    this.entityMappingArr[AppSettings.SECURITY] = new EntityMapping(new SecurityPrepareEdit(this.SECURITY_DERIVED));
    this.entityMappingArr[this.SECURITY_DERIVED] = new EntityMapping(new GeneralEntityPrepareEdit(Security));
    this.entityMappingArr[this.TRADING_PLATFORM_PLAN] = new EntityMapping(new GeneralEntityPrepareEdit(TradingPlatformPlan));
    this.entityMappingArr[this.HISTORYQUOTE] = new EntityMapping(new HistoryquotePrepareEdit(this.securityService,
      this.currencypairService));
    this.addColumnFeqH(DataType.String, 'proposeChangeEntity.entity', true, false,
      {translateValues: TranslateValue.UPPER_CASE});
    this.addColumnFeqH(DataType.String, 'proposeChangeEntity.noteRequest', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'proposeChangeEntity.createdBy', true, false);
    this.addColumn(DataType.NumericInteger, 'proposeChangeEntity.idOwnerEntity', 'OWNER_ENTITY', true, false);
    this.addColumnFeqH(DataType.DateString, 'proposeChangeEntity.creationTime', true, false);
    this.prepareTableAndTranslate();
  }

  ngOnInit(): void {
    this.readData();
  }

  readData(): void {
    this.proposeChangeEntityService.getProposeChangeEntityWithEntity().subscribe(proposeChangeEntityWithEntityList => {
        this.proposeChangeEntityWithEntityList = proposeChangeEntityWithEntityList;
        this.prepareTableAndTranslate();
        this.createTranslatedValueStoreAndFilterField(this.proposeChangeEntityWithEntityList);
      }
    );
  }

  handleEditEntity(proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    const entityMapping = this.getEntityMapping(proposeChangeEntityWithEntity);
    entityMapping.prepareCallParam.prepareForEditEntity(proposeChangeEntityWithEntity.proposedEntity, entityMapping);
  }

  /**
   * In a case when the proposed change was rejected, the state of the corresponding ProposeChangeEntity needs to reflect that.
   */
  handleCloseDialog(proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity, processedActionData: ProcessedActionData) {
    const entityMapping = this.getEntityMapping(proposeChangeEntityWithEntity);
    entityMapping.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        proposeChangeEntityWithEntity.proposeChangeEntity.dataChangeState = ProposeDataChangeState[ProposeDataChangeState.REJECT];
        proposeChangeEntityWithEntity.proposeChangeEntity.noteAcceptReject = processedActionData.data;
        this.proposeChangeEntityService.update(<ProposeChangeEntity>proposeChangeEntityWithEntity.proposeChangeEntity)
          .subscribe(returnEntity => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_PROPOSECHANGE_REJECT');
            this.readData();
          });
      } else {
        this.readData();
      }
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    if (this.selectedEntity) {
      this.contextMenuItems = [{label: 'EDIT', command: () => this.handleEditEntity(this.selectedEntity)}];
      TranslateHelper.translateMenuItems(this.contextMenuItems, this.translateService);
    } else {
      this.contextMenuItems = null;
    }

    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.contextMenuItems
    });
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): HelpIds {
    return HelpIds.HELP_INTRO_PROPOSE_CHANGE_ENTITY;
  }

  private getEntityMapping(proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): EntityMapping {
    const entityMapping = this.entityMappingArr[proposeChangeEntityWithEntity.proposeChangeEntity.entity];
    const entityMappingRedirect = this.entityMappingArr[entityMapping.prepareCallParam.redirectEntityMapping(
      proposeChangeEntityWithEntity.proposedEntity)];
    return entityMappingRedirect || entityMapping;
  }

}

export class EntityMapping {
  visibleDialog: boolean;
  callParam: any = {};
  option: any = {};

  constructor(public prepareCallParam: PrepareCallParam) {
  }
}

export interface PrepareCallParam {
  redirectEntityMapping(proposeEntity: ProposeChangeable): string;

  prepareForEditEntity(entity: ProposeTransientTransfer, entityMapping: EntityMapping): void;
}

export type ProposeChangeable =
  Assetclass
  | Currencypair
  | Historyquote
  | ImportTransactionPlatform
  | ImportTransactionTemplate
  | Security
  | Stockexchange
  | TradingPlatformPlan;




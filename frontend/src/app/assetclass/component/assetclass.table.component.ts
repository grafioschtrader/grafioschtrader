import {Component, OnDestroy} from '@angular/core';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {combineLatest, Subscription} from 'rxjs';
import {AssetclassService} from '../service/assetclass.service';
import {Assetclass} from '../../entities/assetclass';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AssetclassCallParam} from './assetclass.call.param';
import {plainToClass} from 'class-transformer';
import {ConfirmationService, FilterService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {TableCrudSupportMenuSecurity} from '../../lib/datashowbase/table.crud.support.menu.security';
import {HelpIds} from '../../lib/help/help.ids';

/**
 * Shows the asset class as a table.
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollHeight]="'flex'"
      [scrollable]="true"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{'data-container-full': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems"
      [ownerHighlightFn]="isNotSingleModeAndOwner.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{entityNameUpper | translate}}</h4>

      <!-- Custom icon cell template for svg-icon rendering -->
      <ng-template #iconCell let-row let-field="field" let-value="value">
        <svg-icon [name]="value" [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
      </ng-template>

    </configurable-table>

    @if (visibleDialog) {
      <assetclass-edit [visibleDialog]="visibleDialog"
                       [callParam]="callParam"
                       [proposeChangeEntityWithEntity]=""
                       (closeDialog)="handleCloseDialog($event)">
      </assetclass-edit>
    }
  `,
  providers: [DialogService],
  standalone: false
})
export class AssetclassTableComponent extends TableCrudSupportMenuSecurity<Assetclass> implements OnDestroy {
  callParam: AssetclassCallParam = new AssetclassCallParam();

  private readDataSub?: Subscription;


  constructor(private assetclassService: AssetclassService,
    private productIconService: ProductIconService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(AppSettings.ASSETCLASS, assetclassService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService);

    this.addColumn(DataType.String, AppSettings.CATEGORY_TYPE, AppSettings.ASSETCLASS.toUpperCase(), true, false,
      {translateValues: TranslateValue.NORMAL, templateName: AppSettings.OWNER_TEMPLATE});
    this.addColumn(DataType.String, 'assetclassIcon', AppSettings.INSTRUMENT_HEADER, true, false,
      {fieldValueFN: this.getAssetclassIcon.bind(this), templateName: 'icon', width: 25});
    this.addColumn(DataType.String, 'subCategoryNLS.map.en', 'SUB_ASSETCLASS', true, false, {headerSuffix: 'EN'});
    this.addColumn(DataType.String, 'subCategoryNLS.map.de', 'SUB_ASSETCLASS', true, false, {headerSuffix: 'DE'});
    this.addColumn(DataType.String, 'specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true, false,
      {translateValues: TranslateValue.NORMAL});

    this.multiSortMeta.push({field: AppSettings.CATEGORY_TYPE, order: 1});
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: Assetclass): void {
    this.callParam.hasSecurity = entity && this.hasSecurityObject[this.getId(entity)] !== 0;
    this.callParam.assetclass = entity;
  }

  readData(): void {
    this.readDataSub?.unsubscribe();
    combineLatest([this.assetclassService.getAllAssetclass(),
      this.assetclassService.assetclassesHasSecurity()]).subscribe((data: [Assetclass[], number[]]) => {
      const assetclassList: Assetclass[] = plainToClass(Assetclass, data[0]);
      this.callParam.setSuggestionsArrayOfAssetclassList(assetclassList);
      this.createTranslatedValueStoreAndFilterField(assetclassList);
      this.entityList = assetclassList;
      data[1].forEach(keyvalue => this.hasSecurityObject[keyvalue[0]] = keyvalue[1]);
      this.refreshSelectedEntity();
    });
  }

  getAssetclassIcon(assetclass: Assetclass, field: ColumnConfig,
    valueField: any): string {
    return this.productIconService.getIconForAssetclass(assetclass, null);
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_ASSETCLASS;
  }

  ngOnDestroy(): void {
    this.readDataSub?.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

}


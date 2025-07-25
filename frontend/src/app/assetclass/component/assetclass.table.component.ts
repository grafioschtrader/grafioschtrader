import {Component, OnDestroy} from '@angular/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {combineLatest, Subscription} from 'rxjs';
import {AssetclassService} from '../service/assetclass.service';
import {Assetclass} from '../../entities/assetclass';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {AssetclassCallParam} from './assetclass.call.param';
import {plainToClass} from 'class-transformer';
import {ConfirmationService, FilterService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {TableCrudSupportMenuSecurity} from '../../lib/datashowbase/table.crud.support.menu.security';

/**
 * Shows the asset class as a table.
 */
@Component({
  template: `
    <div class="data-container-full" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
               scrollHeight="flex" [scrollable]="true"
               [dataKey]="entityKeyName" sortMode="multiple" [multiSortMeta]="multiSortMeta"
               (sortFunction)="customSort($event)" [customSort]="true"
               stripedRows showGridlines>
        <ng-template #caption>
          <h4>{{entityNameUpper | translate}}</h4>
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field"
                  [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            @for (field of fields; track field) {
              <td [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                @switch (field.templateName) {
                  @case ('owner') {
                    <span [style]='isNotSingleModeAndOwner(field, el)? "font-weight:500": null'>
                   {{getValueByPath(el, field)}}</span>
                  }
                  @case ('icon') {
                    <svg-icon [name]="getValueByPath(el, field)"
                              [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
                  }
                  @default {
                    {{getValueByPath(el, field)}}
                  }
                }
              </td>
            }
          </tr>
        </ng-template>
      </p-table>
      @if (isActivated()) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
    </div>
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

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_BASEDATA_ASSETCLASS;
  }

  ngOnDestroy(): void {
    this.readDataSub?.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

}


import { Component, Injector } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { forkJoin, Subscription } from 'rxjs';
import { AssetclassService } from '../service/assetclass.service';
import { Assetclass } from '../../entities/assetclass';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { AssetclassCallParam } from './assetclass.call.param';
import { plainToInstance } from 'class-transformer';
import { ConfirmationService, FilterService } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { AppSettings } from '../../shared/app.settings';
import { ProductIconService } from '../../securitycurrency/service/product.icon.service';
import { TableCrudSupportMenuSecurity } from '../../lib/datashowbase/table.crud.support.menu.security';
import { HelpIds } from '../../lib/help/help.ids';
import { BaseSettings } from '../../lib/base.settings';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { AssetclassEditComponent } from './assetclass-edit.component';

/**
 * Shows the asset class as a table.
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [scrollHeight]="'flex'"
      [scrollable]="true"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{
        'data-container-full': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems()"
      [ownerHighlightFn]="ownerHighlight"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ entityNameUpper | translate }}</h4>

      <!-- Custom icon cell template for svg-icon rendering -->
      <ng-template #iconCell let-row let-field="field" let-value="value">
        <svg-icon [name]="value" [svgStyle]="{ 'width.px': 14, 'height.px': 14 }"></svg-icon>
      </ng-template>
    </configurable-table>

    @if (visibleDialog()) {
      <assetclass-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam"
        [proposeChangeEntityWithEntity]=""
        (closeDialog)="handleCloseDialog($event)">
      </assetclass-edit>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [TranslateModule, ConfigurableTableComponent, AngularSvgIconModule, AssetclassEditComponent]
})
export class AssetclassTableComponent extends TableCrudSupportMenuSecurity<Assetclass> {
  callParam: AssetclassCallParam = new AssetclassCallParam();
  readonly ownerHighlight = (entity: Assetclass, field: ColumnConfig): boolean =>
    this.isNotSingleModeAndOwner(entity, field);

  private readDataSub?: Subscription;

  constructor(
    private assetclassService: AssetclassService,
    private productIconService: ProductIconService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(
      AppSettings.ASSETCLASS,
      assetclassService,
      confirmationService,
      messageToastService,
      activePanelService,
      dialogService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector
    );

    this.addColumn(DataType.String, AppSettings.CATEGORY_TYPE, AppSettings.ASSETCLASS.toUpperCase(), true, false, {
      translateValues: TranslateValue.NORMAL,
      templateName: BaseSettings.OWNER_TEMPLATE
    });
    this.addColumn(DataType.String, 'assetclassIcon', AppSettings.INSTRUMENT_HEADER, true, false, {
      fieldValueFN: this.getAssetclassIcon.bind(this),
      templateName: 'icon',
      width: 25
    });
    this.addColumn(DataType.String, 'subCategoryNLS.map.en', 'SUB_ASSETCLASS', true, false, { headerSuffix: 'EN' });
    this.addColumn(DataType.String, 'subCategoryNLS.map.de', 'SUB_ASSETCLASS', true, false, { headerSuffix: 'DE' });
    this.addColumn(DataType.String, 'specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true, false, {
      translateValues: TranslateValue.NORMAL
    });

    this.multiSortMeta.push({ field: AppSettings.CATEGORY_TYPE, order: 1 });
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: Assetclass): void {
    this.callParam.hasSecurity = entity && this.hasSecurityObject[this.getId(entity)] !== 0;
    this.callParam.assetclass = entity;
  }

  readData(): void {
    this.readDataSub?.unsubscribe();
    this.readDataSub = forkJoin([
      this.assetclassService.getAllAssetclass(),
      this.assetclassService.assetclassesHasSecurity()
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => {
        const assetclassList = plainToInstance(Assetclass, data[0]);
        this.callParam.setSuggestionsArrayOfAssetclassList(assetclassList);
        this.createTranslatedValueStoreAndFilterField(assetclassList);
        this.hasSecurityObject = Object.fromEntries(data[1]);
        this.entityList.set(assetclassList);
        this.refreshSelectedEntity();
      });
  }

  getAssetclassIcon(assetclass: Assetclass, field: ColumnConfig, valueField: any): string {
    return this.productIconService.getIconForAssetclass(assetclass, null);
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_ASSETCLASS;
  }
}

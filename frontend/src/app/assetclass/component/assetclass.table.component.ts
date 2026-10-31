import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { forkJoin, Subscription } from 'rxjs';
import { AssetclassService } from '../service/assetclass.service';
import { Assetclass } from '../../entities/assetclass';
import { AssetclassCallParam } from './assetclass.call.param';
import { plainToInstance } from 'class-transformer';
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
        <svg-icon [name]="value" [svgStyle]="{ 'width.px': 14, 'height.px': 14 }" />
      </ng-template>
    </configurable-table>

    @if (visibleDialog() && callParam; as params) {
      <assetclass-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="params"
        [proposeChangeEntityWithEntity]=""
        (closeDialog)="handleCloseDialog($event)" />
    }
  `,
  providers: [DialogService],
  imports: [TranslateModule, ConfigurableTableComponent, AngularSvgIconModule, AssetclassEditComponent]
})
export class AssetclassTableComponent extends TableCrudSupportMenuSecurity<Assetclass> {
  private assetclassService: AssetclassService = inject(AssetclassService);
  private productIconService: ProductIconService = inject(ProductIconService);

  callParam: AssetclassCallParam | null = null;
  readonly ownerHighlight = (entity: Assetclass, field: ColumnConfig): boolean =>
    this.isNotSingleModeAndOwner(entity, field);

  private readDataSub?: Subscription;

  constructor() {
    super(AppSettings.ASSETCLASS, inject(AssetclassService));

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

  override prepareCallParam(entity: Assetclass | null): void {
    const id = entity == null ? null : this.getId(entity);
    this.callParam = new AssetclassCallParam(
      entity,
      typeof id === 'number' && this.hasSecurityObject[id] !== 0,
      this.entityList()
    );
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
        this.callParam?.setSuggestionsArrayOfAssetclassList(assetclassList);
        this.createTranslatedValueStoreAndFilterField(assetclassList);
        this.hasSecurityObject = Object.fromEntries(data[1]);
        this.entityList.set(assetclassList);
        this.refreshSelectedEntity();
      });
  }

  getAssetclassIcon(assetclass: Assetclass, field: ColumnConfig, valueField: any): string | null | undefined {
    return this.productIconService.getIconForAssetclass(assetclass, null);
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_ASSETCLASS;
  }
}

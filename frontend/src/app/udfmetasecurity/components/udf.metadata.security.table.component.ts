import { Component, signal, inject } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { UDFMetadataSecurityService } from '../service/udf.metadata.security.service';
import { AppSettings } from '../../shared/app.settings';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { HelpIds } from '../../lib/help/help.ids';
import { UDFMetaTable } from '../../lib/udfmeta/components/udf.metadata.table';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { UDFFillSource, UDFMetadataSecurity, UDFMetadataSecurityParam } from '../model/udf.metadata.security';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { UDFMetadataSecurityEditComponent } from './udf-metadata-security-edit.component';
import { UDFFillProposalsDialogComponent } from './udf-fill-proposals-dialog.component';
import { BaseSettings } from '../../lib/base.settings';

/**
 * Table component for managing security-specific UDF metadata definitions.
 * Displays a comprehensive table of user-defined field metadata for securities with asset class and
 * special instrument type filtering. Unlike general UDF metadata, security metadata includes columns
 * for category types and financial instruments, allowing precise control over field applicability.
 * Supports CRUD operations, sorting, filtering, and special type toggling.
 */
@Component({
  template: `
    <div
      class="data-container-full"
      (click)="onComponentClick($event)"
      #cmDiv
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <configurable-table
        [data]="entityList()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        [dataKey]="entityKeyName"
        [(selection)]="selectedEntity"
        [selectionMode]="'single'"
        [multiSortMeta]="multiSortMeta"
        [sortMode]="'multiple'"
        [customSortFn]="customSorter"
        [valueGetterFn]="valueGetter"
        [scrollable]="true"
        [scrollHeight]="'flex'"
        [stripedRows]="true"
        [showGridlines]="true"
        [contextMenuEnabled]="true"
        [contextMenuItems]="contextMenuItems()"
        [showContextMenu]="isActivated()"
        [containerClass]="''"
        (componentClick)="onComponentClick($event)">
        <h4 caption>{{ entityNameUpper | translate }}</h4>

        <ng-template #iconCell let-row let-field="field" let-value="value">
          <svg-icon [name]="value" [svgStyle]="{ 'width.px': 14, 'height.px': 14 }" />
        </ng-template>
      </configurable-table>
    </div>
    @if (visibleDialog()) {
      <udf-metadata-security-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam"
        (closeDialog)="handleCloseDialog($event)" />
    }
    @if (visibleProposalsDialog()) {
      <udf-fill-proposals-dialog
        [visibleDialog]="visibleProposalsDialog()"
        [field]="proposalsField()"
        (closeDialog)="visibleProposalsDialog.set(false)" />
    }
  `,
  providers: [DialogService],
  imports: [
    TranslateModule,
    AngularSvgIconModule,
    ConfigurableTableComponent,
    UDFMetadataSecurityEditComponent,
    UDFFillProposalsDialogComponent
  ]
})
export class UDFMetadataSecurityTableComponent extends UDFMetaTable<UDFMetadataSecurity> {
  /** Parameters for security metadata editing operations including validation exclusions */
  callParam: UDFMetadataSecurityParam = new UDFMetadataSecurityParam();

  /** Flag indicating this is security-specific (not general) metadata editing */
  isSecurityEdit = true;

  /** Shows the proposals of a language model for {@link proposalsField} */
  readonly visibleProposalsDialog = signal(false);

  /** The field whose proposals are shown */
  readonly proposalsField = signal<UDFMetadataSecurity | null>(null);

  /**
   * Creates the UDF metadata security table component.
   *
   */
  constructor() {
    super(UDFMetadataSecurity, inject(UDFMetadataSecurityService), AppSettings.UDF_METADATA_SECURITY);
    this.addMetadataBaseFields([{ field: 'uiOrder', order: 1 }]);
  }

  /**
   * Adds security-specific columns to the table.
   * Adds asset class types and special instrument types columns after standard metadata columns.
   *
   * @param beforeOthers - True to add columns before standard metadata columns, false for after
   * @protected
   */
  protected override addAdditionalFields(beforeOthers: boolean): void {
    if (!beforeOthers) {
      this.addColumn(DataType.String, 'categoryTypeEnums', AppSettings.ASSETCLASS.toUpperCase(), true, false, {
        translateValues: TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED,
        width: 200
      });
      this.addColumn(DataType.String, 'specialInvestmentInstrumentEnums', 'FINANCIAL_INSTRUMENT', true, false, {
        translateValues: TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED,
        width: 200
      });
      // Set by the server: whether anything fills the field, e.g. not a language model field without a tenant key
      this.addColumnFeqH(DataType.Boolean, 'active', true, false, { templateName: 'check' });
    }
  }

  /**
   * Prepares parameters for opening the security metadata edit dialog.
   * Sets up validation exclusions and entity to edit.
   *
   * @param entity - Security UDF metadata entity to edit, or null for new entry
   */
  /**
   * Adds the proposals of a language model for an own field with the fill source LLM.
   *
   * @param udfMetaData - The selected field
   * @param menuItems - Menu items to extend
   * @protected
   */
  protected override addCustomMenusToSelectedEntity(udfMetaData: UDFMetadataSecurity, menuItems: MenuItem[]): void {
    super.addCustomMenusToSelectedEntity(udfMetaData, menuItems);
    if (udfMetaData?.fillSource === UDFFillSource.LLM && udfMetaData.idUser === this.gps.getIdUser()) {
      menuItems.push({
        label: 'UDF_FILL_PROPOSALS' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => {
          this.proposalsField.set(udfMetaData);
          this.visibleProposalsDialog.set(true);
        }
      });
    }
  }

  override prepareCallParam(entity: UDFMetadataSecurity): void {
    this.beforeEdit(entity, this.callParam);
    this.callParam.uDFMetadataSecurity = entity;
  }

  /**
   * Returns the help context identifier for this component.
   *
   * @returns Help context ID for the security UDF metadata help page
   */
  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_UDF_METADATA_SECURITY;
  }

  /**
   * Loads security UDF metadata from server and clears cached field descriptors.
   * Removing cached descriptors ensures fresh data is loaded when metadata changes.
   */
  override readData(): void {
    super.readData();
    sessionStorage.removeItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY);
  }
}

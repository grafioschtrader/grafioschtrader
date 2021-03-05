import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {ActivatedRoute} from '@angular/router';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {SingleRecordMasterViewBase} from '../../shared/masterdetail/component/single.record.master.view.base';
import {HelpIds} from '../../shared/help/help.ids';
import {AppHelper} from '../../shared/helper/app.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {InputType} from '../../dynamic-form/models/input.type';
import {ImportTransactionPlatformService} from '../service/import.transaction.platform.service';
import {IPlatformTransactionImport} from '../../portfolio/component/iplatform.transaction.import';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {ImportTransactionTemplate} from '../../entities/import.transaction.template';
import {ImportTransactionTemplateTableComponent} from './import-transaction-template-table.component';
import {ParentChildRowSelection} from '../../shared/datashowbase/parent.child.row.selection';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {plainToClass} from 'class-transformer';
import {Assetclass} from '../../entities/assetclass';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';


@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <h4 class="ui-widget-header singleRowTableHeader">{{'IMPORTTRANSACTIONPLATFORM' | translate}}</h4>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm">
      </dynamic-form>

      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems" appendTo="body"></p-contextMenu>
      <br/>
      <import-transaction-template-table></import-transaction-template-table>
    </div>
    <import-transaction-edit-platform *ngIf="visibleEditDialog"
                                      [visibleDialog]="visibleEditDialog"
                                      [callParam]="callParam"
                                      [platformTransactionImportHtmlOptions]="platformTransactionImportHtmlOptions"
                                      (closeDialog)="handleCloseEditDialog($event)">
    </import-transaction-edit-platform>
    <transform-pdf-to-txt-dialog *ngIf="visibleTransformPDFToTxtDialog"
                                 [visibleDialog]="visibleTransformPDFToTxtDialog"
                                 (closeDialog)="handleCloseTransformPDFToTxtDialog()">
    </transform-pdf-to-txt-dialog>
    <template-form-check-dialog *ngIf="visibleTemplateFormCheckDialog"
                                [visibleDialog]="visibleTemplateFormCheckDialog"
                                [importTransactionPlatform]="selectedEntity"
                                (closeDialog)="handleCloseTemplateFormCheckDialog()">
    </template-form-check-dialog>
  `
})
export class ImportTransactionTemplateComponent extends SingleRecordMasterViewBase<ImportTransactionPlatform, ImportTransactionTemplate>
  implements OnInit, OnDestroy, ParentChildRowSelection<ImportTransactionTemplate> {

  private static readonly MAIN_FIELD = 'idTransactionImportPlatform';

  // Access child components
  @ViewChild(ImportTransactionTemplateTableComponent) ittdc: ImportTransactionTemplateTableComponent;


  visibleTransformPDFToTxtDialog: boolean;
  visibleTemplateFormCheckDialog: boolean;
  platformTransactionImportHtmlOptions: ValueKeyHtmlSelectOptions[];


  constructor(private activatedRoute: ActivatedRoute,
              private importTransactionPlatformService: ImportTransactionPlatformService,
              globalparameterService: GlobalparameterService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              translateService: TranslateService) {
    super(globalparameterService, HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE, ImportTransactionTemplateComponent.MAIN_FIELD,
      'IMPORTTRANSACTIONPLATFORM', importTransactionPlatformService,
      confirmationService, messageToastService, activePanelService, translateService);

    this.formConfig = {labelcolumns: 2, nonModal: true};

    this.config = [
      DynamicFieldHelper.createFieldSelectNumber(ImportTransactionTemplateComponent.MAIN_FIELD, 'NAME', false,
        {usedLayoutColumns: 6}),
      DynamicFieldHelper.createFieldInputString('idCsvImportImplementation', 'TRANSACTION_IMPLEMENTATION', 32,
       false, {disabled: true, usedLayoutColumns: 6})
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngOnInit(): void {
    this.importTransactionPlatformService.getPlatformTransactionImport().subscribe(
      (platformTransactionImports: IPlatformTransactionImport[]) => {
        this.platformTransactionImportHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptions('id', 'readableName', platformTransactionImports, true);
        this.configObject.idCsvImportImplementation.valueKeyHtmlOptions = this.platformTransactionImportHtmlOptions;
        setTimeout(() => {
          this.valueChangedMainField();
          this.readData();
        });
      });
  }

  readData(): void {
    this.importTransactionPlatformService.getAllImportTransactionPlatforms().subscribe(
      (importTransactionPlatforms: ImportTransactionPlatform[]) => {
        this.entityList = plainToClass(ImportTransactionPlatform, importTransactionPlatforms);
        this.configObject.idTransactionImportPlatform.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptions('idTransactionImportPlatform',
            'name', importTransactionPlatforms,
            true);
        this.setFieldValues();
      }
    );
  }

  setChildData(selectedEntity: ImportTransactionPlatform): void {
    this.ittdc.parentSelectionChanged(selectedEntity, this);
  }

  prepareEditMenu(): MenuItem[] {
    let menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'TRANSFORM_PDF_TO_TXT',
      command: () => this.transformPDFToTxtDialog()
    });
    menuItems.push({separator: true});
    menuItems = menuItems.concat(this.getBaseEditMenu('IMPORTTRANSACTIONPLATFORM'));

    menuItems.push({separator: true});
    menuItems.push({
      label: 'CHECK_TEMPLATE_FORM',
      command: () => this.testTemplateDialog(),
      disabled: !this.selectedEntity || this.ittdc.isEmpty()
    });

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    menuItems.splice(menuItems.length - 2, 0, ...this.ittdc.prepareEditMenu());

    return menuItems;
  }

  transformPDFToTxtDialog(): void {
    this.visibleTransformPDFToTxtDialog = true;
  }

  testTemplateDialog(): void {
    this.visibleTemplateFormCheckDialog = true;
  }

  handleCloseTransformPDFToTxtDialog(): void {
    this.visibleTransformPDFToTxtDialog = false;
  }

  handleCloseTemplateFormCheckDialog() {
    this.visibleTemplateFormCheckDialog = false;
  }

  rowSelectionChanged(childEntityList: ImportTransactionTemplate[], childSelectedEntity: ImportTransactionTemplate) {
    this.childEntityList = childEntityList;
    this.refreshMenus();
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  protected beforeDelete(entity: ImportTransactionPlatform): ImportTransactionPlatform {
    const importTransactionPlatform = new ImportTransactionPlatform();
    return Object.assign(importTransactionPlatform, entity);
  }

  protected prepareCallParm(entity: ImportTransactionPlatform): void {
    this.callParam = new CallParam(null, entity);
  }
}

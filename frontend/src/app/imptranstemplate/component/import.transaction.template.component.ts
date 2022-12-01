import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {ActivatedRoute} from '@angular/router';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {SingleRecordMasterViewBase} from '../../shared/masterdetail/component/single.record.master.view.base';
import {HelpIds} from '../../shared/help/help.ids';
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
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {
  ImportTransactionTemplateService,
  SuccessFailedImportTransactionTemplate
} from '../service/import.transaction.template.service';
import * as filesaver from '../../shared/filesaver/filesaver';
import {NgxFileDropEntry} from 'ngx-file-drop';
import {AppHelper} from '../../shared/helper/app.helper';

/**
 * Main component of import transaction template. It combines other components like a table.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <div class="flex-two-columns">
        <h4 class="ui-widget-header singleRowTableHeader">{{'IMPORTTRANSACTIONGROUP' | translate}}</h4>
        <div class="right-half" *ngIf="selectedEntity">
          <ngx-file-drop dropZoneLabel="{{'DROP_TEMPLATE_HERE' | translate}}" (onFileDrop)="dropped($event)"
                         dropZoneClassName="drop-zone-trans-long"
                         contentClassName="content-trans">
          </ngx-file-drop>
        </div>
      </div>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm">
      </dynamic-form>
      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
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
              private importTransactionTemplateService: ImportTransactionTemplateService,
              gps: GlobalparameterService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              translateService: TranslateService) {
    super(gps, HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP, ImportTransactionTemplateComponent.MAIN_FIELD,
      'IMPORTTRANSACTIONGROUP', importTransactionPlatformService,
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
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('id', 'readableName', platformTransactionImports, true);
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
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idTransactionImportPlatform',
            'name', importTransactionPlatforms,
            true);
        setTimeout(() => this.setFieldValues());
      }
    );
  }

  setChildData(selectedEntity: ImportTransactionPlatform): void {
    this.ittdc.parentSelectionChanged(selectedEntity, this);
  }

  prepareEditMenu(): MenuItem[] {
    let menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'TRANSFORM_PDF_TO_TXT' + AppSettings.DIALOG_MENU_SUFFIX,
      command: () => this.transformPDFToTxtDialog()
    });
    menuItems.push({separator: true});
    menuItems = menuItems.concat(this.getBaseEditMenu('IMPORTTRANSACTIONGROUP'));
    menuItems.push({
      label: 'EXPORT_ALL_IMPORTTEMPLATES',
      command: () => this.exportAllTemplates(this.selectedEntity),
      disabled: this.ittdc.isEmpty()
    });

    menuItems.push({separator: true});
    menuItems.push({
      label: 'CHECK_TEMPLATE_FORM' + AppSettings.DIALOG_MENU_SUFFIX,
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

  public dropped(files: NgxFileDropEntry[]) {
    AppHelper.processDroppedFiles(files, this.messageToastService, 'tmpl', this.uploadTemplateFiles.bind(this));
  }

  /*
    protected beforeDelete(entity: ImportTransactionPlatform): ImportTransactionPlatform {
      const importTransactionPlatform = new ImportTransactionPlatform();
      return Object.assign(importTransactionPlatform, entity);
    }
  */
  protected prepareCallParam(entity: ImportTransactionPlatform): void {
    this.callParam = new CallParam(null, entity);
  }

  private async exportAllTemplates(itp: ImportTransactionPlatform): Promise<void> {
    const blob = await this.importTransactionTemplateService.getTemplatesByPlatformPlanAsZip(
      this.selectedEntity.idTransactionImportPlatform)
      .catch(error => this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'DOWNLOAD_TEMPLATE_DATA_FAILED'));
    if (blob) {
      filesaver.saveAs(blob, itp.name + '.zip');
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'DOWNLOAD_TEMPLATE_DATA_SUCCESS');
    }
  }

  private uploadTemplateFiles(formData: FormData): void {
    this.importTransactionTemplateService.uploadImportTemplateFiles(this.selectedEntity.idTransactionImportPlatform,
      formData).subscribe((sitt: SuccessFailedImportTransactionTemplate) => {
      this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.INFO, 'UPLOAD_TEMPLATES_SUCCESS',
        {
          successNew: sitt.successNew, successUpdated: sitt.successUpdated, notOwner:
          sitt.notOwner, fileNameError: sitt.fileNameError, contentError: sitt.contentError
        });
      this.setChildData(this.selectedEntity);
    });
  }
}

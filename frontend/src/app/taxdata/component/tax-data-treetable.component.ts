import {Component, OnDestroy, OnInit} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {MenuItem, TreeNode} from 'primeng/api';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {HelpIds} from '../../lib/help/help.ids';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {TaxDataService} from '../service/tax-data.service';
import {TaxCountry, TaxUpload, TaxYear} from '../model/tax-data.model';
import {FileUploadParam} from '../../lib/generaldialog/model/file.upload.param';
import {NgClass} from '@angular/common';
import {Panel} from 'primeng/panel';
import {SharedModule} from 'primeng/api';
import {ConfigurableTreeTableComponent} from '../../lib/datashowbase/configurable-tree-table.component';
import {UploadFileDialogComponent} from '../../lib/generaldialog/component/upload-file-dialog.component';
import {BaseSettings} from '../../lib/base.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TaxCountryCreateComponent} from './tax-country-create.component';
import {TaxYearCreateComponent} from './tax-year-create.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';

enum NodeLevel {
  COUNTRY, YEAR, FILE
}

@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-panel>
        <p-header>
          <h4>{{ 'TAX_DATA' | translate }}</h4>
        </p-header>
      </p-panel>
      <configurable-tree-table
        [data]="treeNodes" [fields]="fields" dataKey="nodeKey"
        sortField="name"
        [(selection)]="selectedNodes" (nodeSelect)="onNodeSelect($event)"
        (nodeUnselect)="onNodeUnselect($event)"
        [contextMenuItems]="contextMenuItems" [showContextMenu]="!!contextMenuItems"
        [valueGetterFn]="getValueByPath.bind(this)">
      </configurable-tree-table>
      @if (visibleUploadFileDialog) {
        <upload-file-dialog [visibleDialog]="visibleUploadFileDialog"
                            [fileUploadParam]="fileUploadParam"
                            (closeDialog)="onUploadDialogClose()">
        </upload-file-dialog>
      }
    </div>
  `,
  standalone: true,
  imports: [NgClass, TranslatePipe, Panel, SharedModule, ConfigurableTreeTableComponent, UploadFileDialogComponent],
  providers: [DialogService]
})
export class TaxDataTreetableComponent extends TreeTableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {

  treeNodes: TreeNode[] = [];
  selectedNodes: TreeNode[] = [];
  contextMenuItems: MenuItem[];
  fileUploadParam: FileUploadParam;
  visibleUploadFileDialog = false;
  isAdmin: boolean;

  private selectedNode: TreeNode;
  private countryNames: Intl.DisplayNames;

  constructor(private taxDataService: TaxDataService,
              private activePanelService: ActivePanelService,
              private messageToastService: MessageToastService,
              private dialogService: DialogService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
    this.isAdmin = AuditHelper.hasAdminRole(gps);
    this.countryNames = new Intl.DisplayNames([gps.getUserLang()], {type: 'region'});
    this.addColumnFeqH(DataType.String, 'name', true, false, {width: 300});
    this.addColumnFeqH(DataType.DateString, 'uploadDate', true, false, {width: 200});
    this.addColumnFeqH(DataType.NumericInteger, 'recordCount', true, false, {width: 150});
    this.translateHeadersAndColumns();
  }

  ngOnInit(): void {
    this.readData();
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_ADMIN;
  }

  onComponentClick(event): void {
    this.resetMenu();
  }

  onNodeSelect(event): void {
    this.selectedNode = event.node;
    this.resetMenu();
  }

  onNodeUnselect(event): void {
    this.selectedNode = null;
    this.resetMenu();
  }

  onUploadDialogClose(): void {
    this.visibleUploadFileDialog = false;
    this.fileUploadParam = null;
    this.readData();
  }

  private readData(): void {
    this.taxDataService.getTree().subscribe((countries: TaxCountry[]) => {
      this.treeNodes = this.buildTree(countries);
    });
  }

  private buildTree(countries: TaxCountry[]): TreeNode[] {
    return countries.map(country => ({
      data: {name: this.countryNames.of(country.countryCode) || country.countryCode, nodeLevel: NodeLevel.COUNTRY, entity: country, nodeKey: 'c_' + country.idTaxCountry},
      children: (country.taxYears || []).map(year => ({
        data: {name: String(year.taxYear), nodeLevel: NodeLevel.YEAR, entity: year, nodeKey: 'y_' + year.idTaxYear},
        children: (year.taxUploads || []).map(upload => ({
          data: {
            name: upload.fileName,
            uploadDate: upload.uploadDate,
            recordCount: upload.recordCount,
            nodeLevel: NodeLevel.FILE,
            entity: upload,
            nodeKey: 'f_' + upload.idTaxUpload
          },
          leaf: true
        })),
        expanded: true
      })),
      expanded: true
    }));
  }

  private resetMenu(): void {
    this.contextMenuItems = this.isAdmin ? this.prepareEditMenu() : null;
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems
    });
  }

  private prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (!this.selectedNode) {
      menuItems.push({label: 'CREATE_TAX_COUNTRY' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.createCountry()});
    } else {
      const level: NodeLevel = this.selectedNode.data.nodeLevel;
      if (level === NodeLevel.COUNTRY) {
        menuItems.push({label: 'CREATE_TAX_YEAR' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.createYear()});
        menuItems.push({label: 'DELETE' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.deleteCountry()});
      } else if (level === NodeLevel.YEAR) {
        menuItems.push({label: 'UPLOAD_TAX_DATA' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.showUploadDialog()});
        menuItems.push({label: 'DELETE' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.deleteYear()});
      } else if (level === NodeLevel.FILE) {
        menuItems.push({label: 'REIMPORT_TAX_DATA' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.reimportFile()});
        menuItems.push({label: 'DELETE' + BaseSettings.DIALOG_MENU_SUFFIX, command: () => this.deleteUpload()});
      }
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  private createCountry(): void {
    this.translateService.get('CREATE_TAX_COUNTRY').subscribe(title => {
      const ref = this.dialogService.open(TaxCountryCreateComponent, {
        header: title, width: '400px', modal: true, closable: true
      });
      ref.onClose.subscribe((countryCode: string) => {
        if (countryCode) {
          this.taxDataService.createCountry({idTaxCountry: null, countryCode}).subscribe(() => this.readData());
        }
      });
    });
  }

  private createYear(): void {
    const country: TaxCountry = this.selectedNode.data.entity;
    this.translateService.get('CREATE_TAX_YEAR').subscribe(title => {
      const ref = this.dialogService.open(TaxYearCreateComponent, {
        header: title, width: '400px', modal: true, closable: true
      });
      ref.onClose.subscribe((taxYear: number) => {
        if (taxYear) {
          this.taxDataService.createYear({idTaxYear: null, idTaxCountry: country.idTaxCountry, taxYear}).subscribe(() => this.readData());
        }
      });
    });
  }

  private showUploadDialog(): void {
    const year: TaxYear = this.selectedNode.data.entity;
    this.fileUploadParam = new FileUploadParam(
      HelpIds.HELP_TAX_DATA,
      null,
      'zip',
      'UPLOAD_TAX_DATA',
      true,
      this.taxDataService,
      year.idTaxYear
    );
    this.visibleUploadFileDialog = true;
  }

  private deleteCountry(): void {
    const country: TaxCountry = this.selectedNode.data.entity;
    this.taxDataService.deleteCountry(country.idTaxCountry).subscribe(() => {
      this.selectedNode = null;
      this.readData();
    });
  }

  private deleteYear(): void {
    const year: TaxYear = this.selectedNode.data.entity;
    this.taxDataService.deleteYear(year.idTaxYear).subscribe(() => {
      this.selectedNode = null;
      this.readData();
    });
  }

  private deleteUpload(): void {
    const upload: TaxUpload = this.selectedNode.data.entity;
    this.taxDataService.deleteUpload(upload.idTaxUpload).subscribe(() => {
      this.selectedNode = null;
      this.readData();
    });
  }

  private reimportFile(): void {
    const upload: TaxUpload = this.selectedNode.data.entity;
    this.taxDataService.reimport(upload.idTaxUpload).subscribe((updated: TaxUpload) => {
      this.messageToastService.showMessageI18n(null, 'REIMPORT_TAX_DATA_SUCCESS', {count: updated.recordCount});
      this.readData();
    });
  }
}

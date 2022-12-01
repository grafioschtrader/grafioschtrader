import {IGlobalMenuAttach} from '../../mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../help/help.ids';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {FieldConfig} from '../../../dynamic-form/models/field.config';
import {FieldFormGroup} from '../../../dynamic-form/models/form.group.definition';
import {FormConfig} from '../../../dynamic-form/models/form.config';
import {Directive, ViewChild} from '@angular/core';
import {DynamicFormComponent} from '../../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {Subscription} from 'rxjs';
import {InfoLevelType} from '../../message/info.leve.type';
import {AppHelper} from '../../helper/app.helper';
import {TranslateService} from '@ngx-translate/core';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {DeleteService} from '../../datashowbase/delete.service';
import {BaseID} from '../../../entities/base.id';
import {CallParam} from '../../maintree/types/dialog.visible';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {AuditHelper} from '../../helper/audit.helper';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {AppSettings} from '../../app.settings';

@Directive()
export abstract class SingleRecordMasterViewBase<T extends BaseID, S> implements IGlobalMenuAttach {

  // Access child components
  @ViewChild(DynamicFormComponent, {static: true}) form: DynamicFormComponent;

  // Form configuration
  formConfig: FormConfig;
  config: FieldFormGroup[] = [];
  configObject: { [name: string]: FieldConfig };

  childEntityList: S[];
  /**
   * Visibility of edit dialog
   */
  visibleEditDialog: boolean;
  // Data to shown
  entityList: T[];
  changeOnMainFieldSub: Subscription;
  callParam: CallParam;
  // For the component Edit-Menu, it shows the same menu items as the context menu
  contextMenuItems: MenuItem[];
  /**
   * The selected entity
   */
  selectedEntity: T;
  /**
   * Avoid looking for changes on the main select box
   */
  protected ignoreChangeOnMonitorField: boolean;

  /**
   * Shows a singe row of a array. If the value of the main field changes then others shown properties are
   * adjusted to this change.
   *
   * @param gps Global parameter service.
   * @param helpId Identification for the help service
   * @param mainFieldId if the value of this input field changes, then other properties in the view will be changed as well
   * @param entityName Name of the entity, it is used for translation
   * @param deleteService Service which support deletion of a selected entity
   * @param confirmationService Confirmation service
   * @param messageToastService Toast message service
   * @param activePanelService Service for active panel
   * @param translateService Service for translation
   */
  constructor(protected gps: GlobalparameterService,
              private helpId: HelpIds,
              private mainFieldId: string,
              private entityName: string,
              protected deleteService: DeleteService,
              protected confirmationService: ConfirmationService,
              protected messageToastService: MessageToastService,
              protected activePanelService: ActivePanelService,
              public translateService: TranslateService) {
  }

  abstract prepareEditMenu(): MenuItem[];

  /**
   * Adjust the child view data on the selected parent data
   */
  abstract setChildData(selectedEntity: T): void;

  abstract readData(): void;

  valueChangedMainField(): void {
    this.changeOnMainFieldSub = this.configObject[this.mainFieldId].formControl.valueChanges.subscribe((key: number) => {
      if (!this.ignoreChangeOnMonitorField) {
        this.selectedEntity = this.entityList.find(entity => entity[this.mainFieldId] === +key);
        this.setFieldValues();
      }
    });
  }

  getBaseEditMenu(entityName: string): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'CREATE|' + entityName + AppSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntityOpenDialog(null),
      disabled: !this.canCreate()
    });
    menuItems.push({
      label: 'EDIT_RECORD|' + entityName + AppSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntityOpenDialog(this.selectedEntity),
      disabled: !this.selectedEntity
    });
    menuItems.push({
      label: 'DELETE_RECORD|' + entityName, disabled: !this.selectedEntity || !this.childEntityList
        || this.childEntityList.length > 0 || !AuditHelper.hasRightsForEditingOrDeleteEntity(this.gps,
          this.selectedEntity),
      command: (event) => this.handleDeleteEntity(this.selectedEntity)
    });
    return menuItems;
  }

  protected canCreate(): boolean {
    return true;
  }

  handleEditEntityOpenDialog(entity: T): void {
    this.prepareCallParam(entity);
    this.visibleEditDialog = true;
  }

  handleCloseEditDialog(processedActionData: ProcessedActionData) {
    this.visibleEditDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.selectedEntity = processedActionData.data;
      this.readData();
    }
  }

  handleDeleteEntity(entity: T) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + this.entityName, () => {
        //    entity = this.beforeDelete(entity);
        this.deleteService.deleteEntity(entity[this.mainFieldId]).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: this.entityName});
          this.selectedEntity = null;
          this.readData();
        });
      });
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  callMeDeactivate(): void {
  }

  hideContextMenu(): void {
  }

  onComponentClick(event): void {
    this.refreshMenus();
  }

  refreshMenus(): void {
    this.contextMenuItems = this.prepareEditMenu();
    this.activePanelService.activatePanel(this, {editMenu: this.contextMenuItems, showMenu: this.prepareShowMenu()});
  }

  public getHelpContextId(): HelpIds {
    return this.helpId;
  }

  destroy(): void {
    this.changeOnMainFieldSub && this.changeOnMainFieldSub.unsubscribe();
  }

  /**
   * Prepare parameter data object for editing component.
   */
  protected abstract prepareCallParam(entity: T): void;

  /*
  protected beforeDelete(entity: T): T {
    return entity;
  }
*/

  protected setFieldValues() {
    this.ignoreChangeOnMonitorField = true;
    if (this.selectedEntity) {
      this.form.transferBusinessObjectToForm(this.selectedEntity);
    } else {
      this.form.setDefaultValues();
    }
    this.setChildData(this.selectedEntity);
    this.ignoreChangeOnMonitorField = false;
  }

  /**
   * Can be overwritten to have a show menu
   */
  protected prepareShowMenu(): MenuItem[] {
    return null;
  }
}

import { DestroyRef, Directive, inject, linkedSignal, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
import { forkJoin, Subscription } from 'rxjs';
import { BaseSettings } from '../../base.settings';
import { DeleteService } from '../../datashowbase/delete.service';
import { DynamicFormComponent } from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { FieldConfig } from '../../dynamic-form/models/field.config';
import { FormConfig } from '../../dynamic-form/models/form.config';
import { FieldFormGroup } from '../../dynamic-form/models/form.group.definition';
import { BaseID } from '../../entities/base.id';
import { AppHelper } from '../../helper/app.helper';
import { AuditHelper } from '../../helper/audit.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { IGlobalMenuAttach } from '../../mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { MessageToastService } from '../../message/message.toast.service';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { ProcessedAction } from '../../types/processed.action';
import { ProcessedActionData } from '../../types/processed.action.data';

/**
 * Abstract base class for components that display and edit a single record from a master list with associated
 * child data. This class provides foundational functionality for master-detail views where users can select a
 * record from a list, view its details, and perform CRUD operations.
 *
 * This class is designed to be framework-independent and can be reused across different applications.
 *
 * @template T - The type of the main entity (must extend BaseID)
 * @template S - The type of child entities associated with the main entity
 * @template CP - The type of call parameters passed to edit dialogs (defaults to any for maximum flexibility)
 */
@Directive()
export abstract class SingleRecordMasterViewBase<T extends BaseID, S, CP = any> implements IGlobalMenuAttach {
  protected gps: GlobalparameterService = inject(GlobalparameterService);
  protected confirmationService: ConfirmationService = inject(ConfirmationService);
  protected messageToastService: MessageToastService = inject(MessageToastService);
  protected activePanelService: ActivePanelService = inject(ActivePanelService);
  public translateService: TranslateService = inject(TranslateService);

  /** Signal-backed accessors preserve the property API used by all master-detail views. */
  protected readonly destroyRef = inject(DestroyRef);
  private menuSubscription?: Subscription;

  // Access child components
  readonly form = viewChild(DynamicFormComponent);

  // Form configuration
  formConfig: FormConfig;
  config: FieldFormGroup[] = [];
  configObject: { [name: string]: FieldConfig };

  private readonly childEntityListState = signal<S[]>([]);

  get childEntityList(): S[] {
    return this.childEntityListState();
  }

  set childEntityList(value: S[]) {
    this.childEntityListState.set(value);
  }
  /** Visibility of edit dialog  */
  private readonly visibleEditDialogState = signal<boolean>(false);

  get visibleEditDialog(): boolean {
    return this.visibleEditDialogState();
  }

  set visibleEditDialog(value: boolean) {
    this.visibleEditDialogState.set(value);
  }
  // Data to shown
  private readonly entityListState = signal<T[]>([]);

  get entityList(): T[] {
    return this.entityListState();
  }

  set entityList(value: T[]) {
    this.entityListState.set(value);
  }
  changeOnMainFieldSub: Subscription;
  /** Parameter object passed to edit dialog components. Type is defined by the consuming application. */
  private readonly callParamState = signal<CP>(null);

  get callParam(): CP {
    return this.callParamState();
  }

  set callParam(value: CP) {
    this.callParamState.set(value);
  }
  // For the component Edit-Menu, it shows the same menu items as the context menu
  private readonly contextMenuItemsState = signal<MenuItem[]>([]);

  get contextMenuItems(): MenuItem[] {
    return this.contextMenuItemsState();
  }

  set contextMenuItems(value: MenuItem[]) {
    this.contextMenuItemsState.set(value);
  }
  /** The selected entity  */
  private readonly selectedEntityState = linkedSignal<T[], T | null>({
    source: this.entityListState,
    computation: (rows, previous) =>
      rows.find((row) => row[this.mainFieldId] === previous?.value?.[this.mainFieldId]) ?? null
  });

  get selectedEntity(): T {
    return this.selectedEntityState();
  }

  set selectedEntity(value: T) {
    this.selectedEntityState.set(value);
  }
  /** Avoid looking for changes on the main select box  */
  protected ignoreChangeOnMonitorField: boolean;

  /**
   * Shows a singe row of a array. If the value of the main field changes then others shown properties are
   * adjusted to this change.
   *
   * @param helpId Identification for the help service
   * @param mainFieldId if the value of this input field changes, then other properties in the view will be changed as well
   * @param entityName Name of the entity, it is used for translation
   * @param deleteService Service which support deletion of a selected entity
   */
  protected constructor(
    private helpId: string,
    private mainFieldId: string,
    private entityName: string,
    protected deleteService: DeleteService
  ) {
    this.destroyRef.onDestroy(() => this.activePanelService.destroyPanel(this));
  }

  abstract prepareEditMenu(): MenuItem[];

  /**
   * Adjust the child view data on the selected parent data
   */
  abstract setChildData(selectedEntity: T): void;

  abstract readData(): void;

  valueChangedMainField(): void {
    this.changeOnMainFieldSub?.unsubscribe();
    this.changeOnMainFieldSub = this.configObject[this.mainFieldId].formControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((key: number) => {
        if (!this.ignoreChangeOnMonitorField) {
          this.selectedEntity = this.entityList.find((entity) => entity[this.mainFieldId] === +key);
          this.setFieldValues();
        }
      });
  }

  getBaseEditMenu(entityName: string): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'CREATE|' + entityName + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntityOpenDialog(null),
      disabled: !this.canCreate()
    });
    menuItems.push({
      label: 'EDIT_RECORD|' + entityName + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntityOpenDialog(this.selectedEntity),
      disabled: !this.selectedEntity
    });
    menuItems.push({
      label: 'DELETE_RECORD|' + entityName,
      disabled:
        !this.selectedEntity ||
        !this.childEntityList ||
        this.childEntityList.length > 0 ||
        !AuditHelper.hasRightsForEditingOrDeleteEntity(this.gps, this.selectedEntity),
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
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + this.entityName,
      () => {
        //    entity = this.beforeDelete(entity);
        this.deleteService
          .deleteEntity(entity[this.mainFieldId])
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((response) => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: this.entityName
            });
            if (this.selectedEntity?.[this.mainFieldId] === entity[this.mainFieldId]) {
              this.selectedEntity = null;
            }
            this.readData();
          });
      }
    );
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  callMeDeactivate(): void {}

  hideContextMenu(): void {}

  onComponentClick(event): void {
    this.refreshMenus();
  }

  refreshMenus(): void {
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = forkJoin({
      editMenu: TranslateHelper.translateMenuItems$(this.prepareEditMenu(), this.translateService),
      showMenu: TranslateHelper.translateMenuItems$(this.prepareShowMenu(), this.translateService)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((menus) => {
        this.contextMenuItems = menus.editMenu;
        this.activePanelService.activatePanel(this, menus);
      });
  }

  protected reSetHelpId(helpId: string): void {
    this.helpId = helpId;
  }

  public getHelpContextId(): string {
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
      this.form().transferBusinessObjectToForm(this.selectedEntity);
    } else {
      this.form().setDefaultValues();
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

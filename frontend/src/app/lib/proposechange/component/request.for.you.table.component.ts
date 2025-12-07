import {ChangeDetectorRef, Component, OnInit, ViewChild, ViewContainerRef} from '@angular/core';
import {UserSettingsService} from '../../services/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {TableConfigBase} from '../../datashowbase/table.config.base';
import {IGlobalMenuAttach} from '../../mainmenubar/component/iglobal.menu.attach';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {EntityMapping} from './general.entity.prepare.edit';
import {ProposeChangeEntityWithEntity} from '../model/propose.change.entity.whit.entity';
import {ProposeChangeEntityService} from '../service/propose.change.entity.service';
import {ProposeDataChangeState} from '../../types/propose.data.change.state';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {ProposeChangeEntity} from '../../entities/propose.change.entity';
import {TranslateValue} from '../../datashowbase/column.config';
import {FilterService, MenuItem} from 'primeng/api';
import {TranslateHelper} from '../../helper/translate.helper';
import {HelpIds} from '../../help/help.ids';
import {EntityPrepareRegistry} from '../service/entity.prepare.registry';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';

/**
 * Displays proposed changes on entities in a table format.
 * This component provides a generic propose change workflow that works with any entity type
 * registered in the EntityPrepareRegistry. The component is entity-agnostic, relying on
 * registered handlers to prepare entities for editing and registered edit components to
 * display the edit dialogs.
 *
 * The registry pattern allows this lib component to be reused across different applications
 * without knowing about specific entity types at compile time.
 */
@Component({
  selector: 'request-for-you-table',
  template: `
    <configurable-table
      (componentClick)="onComponentClick($event)"
      [data]="proposeChangeEntityWithEntityList"
      [fields]="fields"
      [dataKey]="'proposeChangeEntity.idProposeRequest'"
      [(selection)]="selectedEntity"
      [contextMenuAppendTo]="'body'"
      [contextMenuItems]="contextMenuItems"
      [showContextMenu]="isActivated()"
      [containerClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <h4 caption>{{ 'PROPOSE_CHANGE_ENTITY_FOR_USER' | translate }} {{ gps.getIdUser() }}</h4>
    </configurable-table>

    <ng-template #editDialogContainer></ng-template>
  `,
  standalone: true,
  imports: [ConfigurableTableComponent, TranslateModule]
})
export class RequestForYouTableComponent extends TableConfigBase implements OnInit, IGlobalMenuAttach {

  @ViewChild('editDialogContainer', {read: ViewContainerRef}) editDialogContainer: ViewContainerRef;

  contextMenuItems: MenuItem[] = [];
  entityMappingArr: { [key: string]: EntityMapping } = {};
  proposeChangeEntityWithEntityList: ProposeChangeEntityWithEntity[];
  selectedEntity: ProposeChangeEntityWithEntity;

  /**
   * Creates the request for you table component.
   * Initializes table configuration for displaying proposed change requests.
   *
   * @param entityPrepareRegistry - Registry containing entity handlers and edit components
   * @param proposeChangeEntityService - Service for managing propose change entities
   * @param activePanelService - Service for managing active panel state
   * @param messageToastService - Service for displaying toast messages
   * @param filterService - PrimeNG filter service for table filtering
   * @param translateService - Translation service for i18n
   * @param gps - Global parameter service for user settings
   * @param usersettingsService - User settings service for persisting table state
   */
  constructor(
    private entityPrepareRegistry: EntityPrepareRegistry,
    private proposeChangeEntityService: ProposeChangeEntityService,
    private activePanelService: ActivePanelService,
    private messageToastService: MessageToastService,
    private cdr: ChangeDetectorRef,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);

    this.addColumnFeqH(DataType.String, 'proposeChangeEntity.entity', true, false,
      {translateValues: TranslateValue.UPPER_CASE});
    this.addColumnFeqH(DataType.String, 'proposeChangeEntity.noteRequest', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'proposeChangeEntity.createdBy', true, false);
    this.addColumn(DataType.NumericInteger, 'proposeChangeEntity.idOwnerEntity', 'OWNER_ENTITY', true, false);
    this.addColumnFeqH(DataType.DateTimeSecondString, 'proposeChangeEntity.creationTime', true, false);
    this.prepareTableAndTranslate();
  }

  ngOnInit(): void {
    this.readData();
  }

  /**
   * Loads all propose change entities with their associated proposed entities from the backend.
   */
  readData(): void {
    this.proposeChangeEntityService.getProposeChangeEntityWithEntity().subscribe(proposeChangeEntityWithEntityList => {
        this.proposeChangeEntityWithEntityList = proposeChangeEntityWithEntityList;
        this.prepareTableAndTranslate();
        this.createTranslatedValueStoreAndFilterField(this.proposeChangeEntityWithEntityList);
      }
    );
  }

  /**
   * Prepares and displays the edit dialog for a proposed entity change.
   * Lazily initializes the entity mapping if not already created, then uses the registered
   * prepare handler to configure the edit dialog parameters. Subscribes to the preparation
   * observable to handle both synchronous and asynchronous preparation.
   *
   * @param proposeChangeEntityWithEntity - The proposed change with its entity data
   */
  handleEditEntity(proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    const entityMapping = this.getEntityMapping(proposeChangeEntityWithEntity);
    if (entityMapping) {
      const prepareObservable = entityMapping.prepareCallParam.prepareForEditEntity(
        proposeChangeEntityWithEntity.proposedEntity,
        entityMapping
      );

      // Subscribe to the preparation observable (handles both sync and async cases)
      prepareObservable.subscribe(() => {
        // After preparation is complete, load the edit component dynamically
        if (entityMapping.visibleDialog && entityMapping.editComponentType) {
          this.loadEditComponent(proposeChangeEntityWithEntity, entityMapping);
        }
      });
    }
  }

  /**
   * Dynamically loads and displays the edit component for the given entity type.
   * Creates a component instance with the required inputs and output event handlers.
   *
   * @param proposeChangeEntityWithEntity - The proposed change with its entity data
   * @param entityMapping - The entity mapping containing component type and parameters
   * @protected
   */
  protected loadEditComponent(proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity,
    entityMapping: EntityMapping): void {
    if (this.editDialogContainer) {
      this.editDialogContainer.clear();

      // Create component with proper type casting to avoid TypeScript error
      const componentRef = this.editDialogContainer.createComponent(entityMapping.editComponentType as any);
      const instance = componentRef.instance;

      // Set inputs based on component type
      // This assumes all edit components follow a common interface pattern
      if (instance) {
        this.setComponentInputs(instance, entityMapping, proposeChangeEntityWithEntity);
        this.setComponentOutputs(instance, proposeChangeEntityWithEntity);

        // Manually trigger change detection to ensure inputs are processed
        componentRef.changeDetectorRef.detectChanges();

        // Also trigger parent change detection
        this.cdr.detectChanges();
      }
    }
  }

  /**
   * Sets inputs on the dynamically created edit component.
   * Each edit component type may have different input properties, so we check for their existence.
   *
   * @param instance - The component instance
   * @param entityMapping - The entity mapping with parameters
   * @param proposeChangeEntityWithEntity - The proposed change entity
   * @protected
   */
  protected setComponentInputs(instance: any,  entityMapping: EntityMapping,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    // Common inputs - directly set without checking (Angular @Input properties might not be enumerable)
    instance.visibleDialog = entityMapping.visibleDialog;
    instance.callParam = entityMapping.callParam;
    instance.proposeChangeEntityWithEntity = proposeChangeEntityWithEntity;

    // Entity-specific inputs
    if (entityMapping.option) {
      instance.platformTransactionImportHtmlOptions = entityMapping.option;
    }

    // Alternative property names for different component types
    instance.visibleEditCurrencypairDialog = entityMapping.visibleDialog;
    instance.securityCurrencypairCallParam = entityMapping.callParam;
    instance.visibleEditSecurityDialog = entityMapping.visibleDialog;
    instance.securityCallParam = entityMapping.callParam;
  }

  /**
   * Sets up output event handlers on the dynamically created edit component.
   * Subscribes to the closeDialog event to handle dialog dismissal.
   *
   * @param instance - The component instance
   * @param proposeChangeEntityWithEntity - The proposed change entity
   * @protected
   */
  protected setComponentOutputs(instance: any, proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): void {
    if (instance.closeDialog) {
      instance.closeDialog.subscribe((processedActionData: ProcessedActionData) => {
        this.handleCloseDialog(proposeChangeEntityWithEntity, processedActionData);
      });
    }
  }

  /**
   * Handles closing of the edit dialog.
   * If the proposed change was rejected, updates the ProposeChangeEntity state.
   * Otherwise, reloads the data to reflect any changes.
   *
   * @param proposeChangeEntityWithEntity - The proposed change that was being edited
   * @param processedActionData - Result of the edit operation
   */
  handleCloseDialog(proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity, processedActionData: ProcessedActionData): void {
    const entityMapping = this.getEntityMapping(proposeChangeEntityWithEntity);
    if (entityMapping) {
      entityMapping.visibleDialog = false;
    }

    // Clear the dynamically loaded component
    if (this.editDialogContainer) {
      this.editDialogContainer.clear();
    }

    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        proposeChangeEntityWithEntity.proposeChangeEntity.dataChangeState = ProposeDataChangeState[ProposeDataChangeState.REJECT];
        proposeChangeEntityWithEntity.proposeChangeEntity.noteAcceptReject = processedActionData.data;
        this.proposeChangeEntityService.update(<ProposeChangeEntity>proposeChangeEntityWithEntity.proposeChangeEntity)
          .subscribe(returnEntity => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_PROPOSECHANGE_REJECT');
            this.readData();
          });
      } else {
        this.readData();
      }
    }
  }

  /**
   * Checks if this component is the currently active panel.
   *
   * @returns True if this component is active, false otherwise
   */
  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  /**
   * Handles component click events.
   * Updates the context menu based on whether an entity is selected.
   *
   * @param event - The click event
   */
  onComponentClick(event): void {
    if (this.selectedEntity) {
      this.contextMenuItems = [{label: 'EDIT', command: () => this.handleEditEntity(this.selectedEntity)}];
      TranslateHelper.translateMenuItems(this.contextMenuItems, this.translateService);
    } else {
      this.contextMenuItems = null;
    }

    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.contextMenuItems
    });
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  /**
   * Returns the help context ID for this component.
   *
   * @returns Help ID string
   */
  getHelpContextId(): string {
    return HelpIds.HELP_INTRO_PROPOSE_CHANGE_ENTITY;
  }

  /**
   * Retrieves or creates the entity mapping for a given proposed change.
   * Lazily initializes entity mappings on first use by looking up handlers in the registry.
   * Also handles entity redirection for cases like derived securities.
   *
   * @param proposeChangeEntityWithEntity - The proposed change entity
   * @returns The entity mapping, or undefined if no handler is registered for this entity type
   * @protected
   */
  protected getEntityMapping(proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity): EntityMapping | undefined {
    const entityType = proposeChangeEntityWithEntity.proposeChangeEntity.entity;

    // Lazy initialization of entity mapping
    if (!this.entityMappingArr[entityType]) {
      const prepareHandler = this.entityPrepareRegistry.getPrepareHandler(entityType);
      const editComponent = this.entityPrepareRegistry.getEditComponent(entityType);

      if (prepareHandler && editComponent) {
        this.entityMappingArr[entityType] = new EntityMapping(prepareHandler);
        this.entityMappingArr[entityType].editComponentType = editComponent;
      } else {
        console.warn(`No handler or component registered for entity type: ${entityType}`);
        return undefined;
      }
    }

    const entityMapping = this.entityMappingArr[entityType];

    // Check if entity should be redirected to a different mapping
    const redirectEntityType = entityMapping.prepareCallParam.redirectEntityMapping(
      proposeChangeEntityWithEntity.proposedEntity
    );

    if (redirectEntityType) {
      // Ensure redirect mapping exists
      if (!this.entityMappingArr[redirectEntityType]) {
        const prepareHandler = this.entityPrepareRegistry.getPrepareHandler(redirectEntityType);
        const editComponent = this.entityPrepareRegistry.getEditComponent(redirectEntityType);

        if (prepareHandler && editComponent) {
          this.entityMappingArr[redirectEntityType] = new EntityMapping(prepareHandler);
          this.entityMappingArr[redirectEntityType].editComponentType = editComponent;
        }
      }
      return this.entityMappingArr[redirectEntityType];
    }

    return entityMapping;
  }
}

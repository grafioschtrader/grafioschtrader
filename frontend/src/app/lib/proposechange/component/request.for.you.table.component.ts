import {
  Component,
  ComponentRef,
  DestroyRef,
  inject,
  OnInit,
  reflectComponentType,
  signal,
  Type,
  ViewContainerRef,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { DataType } from '../../dynamic-form/models/data.type';
import { TranslateModule } from '@ngx-translate/core';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { TableConfigBase } from '../../datashowbase/table.config.base';
import { IGlobalMenuAttach } from '../../mainmenubar/component/iglobal.menu.attach';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { EntityMapping } from './general.entity.prepare.edit';
import { ProposeChangeEntityWithEntity } from '../model/propose.change.entity.whit.entity';
import { ProposeChangeEntityService } from '../service/propose.change.entity.service';
import { ProposeDataChangeState } from '../../types/propose.data.change.state';
import { InfoLevelType } from '../../message/info.leve.type';
import { MessageToastService } from '../../message/message.toast.service';
import { ProposeChangeEntity } from '../../entities/propose.change.entity';
import { TranslateValue } from '../../datashowbase/column.config';
import { MenuItem } from '@openng/optimus-ui/api';
import { TranslateHelper } from '../../helper/translate.helper';
import { HelpIds } from '../../help/help.ids';
import { EntityPrepareRegistry } from '../service/entity.prepare.registry';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';

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
      [data]="proposeChangeEntityWithEntityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'proposeChangeEntity.idProposeRequest'"
      [(selection)]="selectedEntity"
      [contextMenuAppendTo]="'body'"
      [contextMenuItems]="contextMenuItems()"
      [showContextMenu]="isActivated()"
      [containerClass]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <h4 caption>{{ 'PROPOSE_CHANGE_ENTITY_FOR_USER' | translate }} {{ gps.getIdUser() }}</h4>
    </configurable-table>

    <ng-template #editDialogContainer />
  `,
  imports: [ConfigurableTableComponent, TranslateModule]
})
export class RequestForYouTableComponent extends TableConfigBase implements OnInit, IGlobalMenuAttach {
  private entityPrepareRegistry: EntityPrepareRegistry = inject(EntityPrepareRegistry);
  private proposeChangeEntityService: ProposeChangeEntityService = inject(ProposeChangeEntityService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private messageToastService: MessageToastService = inject(MessageToastService);

  readonly editDialogContainer = viewChild('editDialogContainer', { read: ViewContainerRef });

  /** Translated menu of the selected request, shown as context menu and in the menu bar */
  readonly contextMenuItems = signal<MenuItem[]>([]);
  entityMappingArr: { [key: string]: EntityMapping } = {};
  readonly proposeChangeEntityWithEntityList = signal<ProposeChangeEntityWithEntity[]>([]);
  readonly selectedEntity = signal<ProposeChangeEntityWithEntity>(null);

  private readonly destroyRef = inject(DestroyRef);
  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription: Subscription;

  /**
   * Creates the request for you table component.
   * Initializes table configuration for displaying proposed change requests.
   *
   */
  constructor() {
    super();

    this.addColumnFeqH(DataType.String, 'proposeChangeEntity.entity', true, false, {
      translateValues: TranslateValue.UPPER_CASE
    });
    this.addColumnFeqH(DataType.String, 'proposeChangeEntity.noteRequest', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'proposeChangeEntity.createdBy', true, false);
    this.addColumn(DataType.NumericInteger, 'proposeChangeEntity.idOwnerEntity', 'OWNER_ENTITY', true, false);
    this.addColumnFeqH(DataType.DateTimeSecondString, 'proposeChangeEntity.creationTime', true, false);
    this.prepareTableAndTranslate();
    this.destroyRef.onDestroy(() => this.menuSubscription?.unsubscribe());
  }

  ngOnInit(): void {
    this.readData();
  }

  /**
   * Loads all propose change entities with their associated proposed entities from the backend.
   */
  readData(): void {
    this.proposeChangeEntityService
      .getProposeChangeEntityWithEntity()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((proposeChangeEntityWithEntityList) => {
        this.prepareTableAndTranslate();
        this.createTranslatedValueStoreAndFilterField(proposeChangeEntityWithEntityList);
        this.proposeChangeEntityWithEntityList.set(proposeChangeEntityWithEntityList);
      });
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
        if (entityMapping.visibleDialog && entityMapping.loadEditComponent) {
          this.resolveEditComponentType(entityMapping).then(() =>
            this.loadEditComponent(proposeChangeEntityWithEntity, entityMapping)
          );
        }
      });
    }
  }

  /**
   * Loads the edit component of the mapping on first use and keeps its class for later openings. A failed import is
   * reported as a toast; the returned promise then stays pending, so no dialog is created.
   *
   * @param entityMapping - The entity mapping whose edit component is needed
   * @returns Promise that resolves once editComponentType is set
   * @protected
   */
  protected resolveEditComponentType(entityMapping: EntityMapping): Promise<void> {
    if (entityMapping.editComponentType) {
      return Promise.resolve();
    }
    return entityMapping.loadEditComponent().then(
      (componentType) => {
        entityMapping.editComponentType = componentType;
      },
      () => {
        this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'MSG_LOAD_NEW_VERSION');
        return new Promise<void>(() => {});
      }
    );
  }

  /**
   * Dynamically loads and displays the edit component for the given entity type.
   * Creates a component instance with the required inputs and output event handlers.
   *
   * @param proposeChangeEntityWithEntity - The proposed change with its entity data
   * @param entityMapping - The entity mapping containing component type and parameters
   * @protected
   */
  protected loadEditComponent(
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity,
    entityMapping: EntityMapping
  ): void {
    const editDialogContainer = this.editDialogContainer();
    if (editDialogContainer) {
      editDialogContainer.clear();

      const componentRef = editDialogContainer.createComponent(entityMapping.editComponentType as Type<any>);
      // setInput marks the created component and this view for check, whatever change detection strategy they use
      this.setComponentInputs(componentRef, entityMapping, proposeChangeEntityWithEntity);
      this.setComponentOutputs(componentRef.instance, proposeChangeEntityWithEntity);
    }
  }

  /**
   * Sets the inputs of the dynamically created edit component through setInput. The edit components of the entity
   * types name their inputs differently, so only the inputs the component type declares are set.
   *
   * @param componentRef - Reference of the created component
   * @param entityMapping - The entity mapping with parameters
   * @param proposeChangeEntityWithEntity - The proposed change entity
   * @protected
   */
  protected setComponentInputs(
    componentRef: ComponentRef<any>,
    entityMapping: EntityMapping,
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity
  ): void {
    // The edit components name their inputs differently; only the inputs a component declares are set
    const declared = new Set(
      (reflectComponentType(componentRef.componentType)?.inputs ?? []).map((input) => input.templateName)
    );
    const inputs: { [name: string]: unknown } = {
      visibleDialog: entityMapping.visibleDialog,
      callParam: entityMapping.callParam,
      proposeChangeEntityWithEntity,
      visibleEditCurrencypairDialog: entityMapping.visibleDialog,
      securityCurrencypairCallParam: entityMapping.callParam,
      visibleEditSecurityDialog: entityMapping.visibleDialog,
      securityCallParam: entityMapping.callParam
    };
    if (entityMapping.option) {
      inputs['platformTransactionImportHtmlOptions'] = entityMapping.option;
    }
    Object.entries(inputs)
      .filter(([name]) => declared.has(name))
      .forEach(([name, value]) => componentRef.setInput(name, value));
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
  handleCloseDialog(
    proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity,
    processedActionData: ProcessedActionData
  ): void {
    const entityMapping = this.getEntityMapping(proposeChangeEntityWithEntity);
    if (entityMapping) {
      entityMapping.visibleDialog = false;
    }

    // Clear the dynamically loaded component
    const editDialogContainer = this.editDialogContainer();
    if (editDialogContainer) {
      editDialogContainer.clear();
    }

    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        proposeChangeEntityWithEntity.proposeChangeEntity.dataChangeState =
          ProposeDataChangeState[ProposeDataChangeState.REJECT];
        proposeChangeEntityWithEntity.proposeChangeEntity.noteAcceptReject = processedActionData.data;
        this.proposeChangeEntityService
          .update(<ProposeChangeEntity>proposeChangeEntityWithEntity.proposeChangeEntity)
          .subscribe((returnEntity) => {
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
    const selected = this.selectedEntity();
    const menuItems: MenuItem[] = selected ? [{ label: 'EDIT', command: () => this.handleEditEntity(selected) }] : null;
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = TranslateHelper.translateMenuItems$(menuItems, this.translateService).subscribe(
      (translated) => {
        this.contextMenuItems.set(translated ?? []);
        this.activePanelService.activatePanel(this, { showMenu: this.getMenuShowOptions(), editMenu: translated });
      }
    );
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

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
      const loadEditComponent = this.entityPrepareRegistry.getEditComponentLoader(entityType);

      if (prepareHandler && loadEditComponent) {
        this.entityMappingArr[entityType] = new EntityMapping(prepareHandler);
        this.entityMappingArr[entityType].loadEditComponent = loadEditComponent;
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
        const loadEditComponent = this.entityPrepareRegistry.getEditComponentLoader(redirectEntityType);

        if (prepareHandler && loadEditComponent) {
          this.entityMappingArr[redirectEntityType] = new EntityMapping(prepareHandler);
          this.entityMappingArr[redirectEntityType].loadEditComponent = loadEditComponent;
        }
      }
      return this.entityMappingArr[redirectEntityType];
    }

    return entityMapping;
  }
}

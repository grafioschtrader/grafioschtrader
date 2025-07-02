import {Component, OnInit} from '@angular/core';
import {TableConfigBase} from '../datashowbase/table.config.base';
import {Globalparameters} from '../../lib/entities/globalparameters';
import {FilterService, MenuItem} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {UserSettingsService} from '../service/user.settings.service';
import {IGlobalMenuAttach} from '../mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../help/help.ids';
import {ActivePanelService} from '../mainmenubar/service/active.panel.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../datashowbase/column.config';
import {AppHelper} from '../helper/app.helper';
import {AppSettings} from '../app.settings';
import {AuditHelper} from '../helper/audit.helper';
import {User} from '../../lib/entities/user';
import {ProcessedActionData} from '../types/processed.action.data';
import {ProcessedAction} from '../types/processed.action';
import {TranslateHelper} from '../helper/translate.helper';

/**
 * Shows global settings in table. Here TableCrudSupportMenu is not derived because no entities can be deleted or added.
 */
@Component({
    template: `
        <div class="data-container" (click)="onComponentClick($event)" #cmDiv
             [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

            <p-table [columns]="fields" [value]="globalparametersList" selectionMode="single"
                     [(selection)]="selectedEntity" dataKey="propertyName" responsiveLayout="scroll"
                     (sortFunction)="customSort($event)" [customSort]="true"
                     stripedRows showGridlines>
                <ng-template #caption>
                    <h4>{{'GLOBAL_SETTINGS' | translate}}</h4>
                </ng-template>
                <ng-template #header let-fields>
                    <tr>
                        <td style="max-width:24px">
                        <th *ngFor="let field of fields" [pSortableColumn]="field.field"
                            [pTooltip]="field.headerTooltipTranslated"
                            [style.max-width.px]="field.width"
                            [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                            {{field.headerTranslated}}
                            <p-sortIcon [field]="field.field"></p-sortIcon>
                        </th>
                    </tr>
                </ng-template>
                <ng-template #body let-expanded="expanded" let-el let-columns="fields">
                    <tr [pSelectableRow]="el">
                        <td style="max-width:24px">
                            <a *ngIf="el.propertyBlobAsText" href="#" [pRowToggler]="el">
                                <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
                            </a>
                        </td>
                        <td *ngFor="let field of fields" [style.max-width.px]="field.width"
                            [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                            <ng-container [ngSwitch]="field.templateName">
                                <ng-container *ngSwitchCase="'check'">
                                    <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}"
                                             aria-hidden="true"></i></span>
                                </ng-container>
                                <ng-container *ngSwitchDefault>
                                  <span [pTooltip]="getValueByPath(el, field)">{{getValueByPath(el, field)}}</span>
                                </ng-container>
                            </ng-container>
                        </td>
                    </tr>
                </ng-template>
                <ng-template #expandedrow let-gp let-columns="fields">
                    <tr>
                        <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
              <textarea [rows]="10" pTextarea style="width: 100%;"
                        readonly="true">{{gp.propertyBlobAsText}}</textarea>
                        </td>
                    </tr>
                </ng-template>
            </p-table>
            <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
        </div>
        <globalsettings-edit *ngIf="visibleDialog"
                             [visibleDialog]="visibleDialog"
                             [globalparameters]="selectedEntity"
                             (closeDialog)="handleGlobalparameterCloseDialog($event)">
        </globalsettings-edit>
    `,
    standalone: false
})
export class GlobalSettingsTableComponent extends TableConfigBase implements OnInit, IGlobalMenuAttach {

    contextMenuItems: MenuItem[] = [];
    editMenu: MenuItem;
    globalparametersList: Globalparameters[];
    selectedEntity: Globalparameters;
    callParam: User;
    visibleDialog = false;
    private readonly PROPERTY_NAME = 'propertyName';

    constructor(private activePanelService: ActivePanelService,
                filterService: FilterService,
                translateService: TranslateService,
                gps: GlobalparameterService,
                usersettingsService: UserSettingsService) {
        super(filterService, usersettingsService, translateService, gps);

        this.addColumn(DataType.String, this.PROPERTY_NAME, 'PROPERTY_NAME_DESC', true, false,
            {translateValues: TranslateValue.NORMAL, width: 450});
        this.addColumnFeqH(DataType.String, 'propertyValue', true, false,
            {fieldValueFN: this.getProperty.bind(this)});
        this.addColumnFeqH(DataType.Boolean, 'changedBySystem', true, false,
            {templateName: 'check'});
        this.addColumn(DataType.String, this.PROPERTY_NAME + '1', TranslateHelper.camelToUnderscoreCase(this.PROPERTY_NAME), true, false,
            {width: 200, fieldValueFN: this.getPropertyName1.bind(this)});
        this.editMenu = {
            label: 'EDIT_RECORD|GLOBAL_SETTINGS' + AppSettings.DIALOG_MENU_SUFFIX,
            command: (event) => this.handleEditEntity(this.selectedEntity),
            disabled: !AuditHelper.hasAdminRole(this.gps)
        };
        TranslateHelper.translateMenuItems([this.editMenu], translateService);
    }

    ngOnInit(): void {
        this.readData();
    }

    getProperty(entity: Globalparameters, field: ColumnConfig): string {
        if (entity.propertyDate) {
            return AppHelper.getDateByFormat(this.gps, entity.propertyDate);
        } else if (entity.propertyInt != null) {
            return AppHelper.numberIntegerFormat(this.gps, entity.propertyInt);
        } else {
            return entity.propertyString;
        }
    }

    /**
     * Is needed for the difference to the translated property name, otherwise sorting is not working
     */
    getPropertyName1(dataobject: any, field: ColumnConfig, valueField: any): any {
        return dataobject[this.PROPERTY_NAME];
    }

    isActivated(): boolean {
        return this.activePanelService.isActivated(this);
    }

    onComponentClick(event): void {
        this.resetMenu(this.selectedEntity);
    }

    handleEditEntity(globalparameters: Globalparameters): void {
        this.visibleDialog = true;
    }

    handleGlobalparameterCloseDialog(processedActionData: ProcessedActionData): void {
        this.visibleDialog = false;
        if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
            this.readData();
        }
    }

    hideContextMenu(): void {
    }

    callMeDeactivate(): void {
    }

    getHelpContextId(): HelpIds {
        return HelpIds.HELP_GLOBAL_SETTINGS;
    }

    protected getEditMenu(): MenuItem[] {
        this.contextMenuItems = [];
        if (this.selectedEntity) {
            this.contextMenuItems.push(this.editMenu);
        }
        return this.contextMenuItems;
    }

    private readData(): void {
        this.gps.getAllGlobalparameters().subscribe(globalparametersList => {
            this.globalparametersList = globalparametersList;
            this.prepareTableAndTranslate();
            this.createTranslatedValueStoreAndFilterField(globalparametersList);
            if (this.selectedEntity) {
                this.resetMenu(this.globalparametersList.find(gp => gp.propertyName === this.selectedEntity.propertyName));
            }
        });
    }

    private resetMenu(gp: Globalparameters): void {
        this.selectedEntity = gp;
        this.activePanelService.activatePanel(this, {
            showMenu: this.getMenuShowOptions(),
            editMenu: this.getEditMenu()
        });
    }

}

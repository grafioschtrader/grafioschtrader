import {TableCrudSupportMenuSecurity} from '../../datashowbase/table.crud.support.menu.security';
import {Component, OnDestroy} from '@angular/core';
import {UDFMetadataSecurityService} from '../service/udf.metadata.security.service';
import {AppSettings} from '../../app.settings';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {UserSettingsService} from '../../service/user.settings.service';
import {UDFMetadataSecurity, UDFMetadataSecurityParam} from '../model/udf.metadata';
import {DataType} from '../../../dynamic-form/models/data.type';
import {TranslateValue} from '../../datashowbase/column.config';
import {plainToInstance} from 'class-transformer';

@Component({
    template: `
        <div class="data-container-full" (click)="onComponentClick($event)" #cmDiv
             [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

            <p-table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
                     styleClass="p-datatable-striped p-datatable-gridlines"
                     responsiveLayout="scroll" scrollHeight="flex" [scrollable]="true"
                     [dataKey]="entityKeyName" sortMode="multiple" [multiSortMeta]="multiSortMeta"
                     (sortFunction)="customSort($event)" [customSort]="true">
                <ng-template pTemplate="caption">
                    <h4>{{entityNameUpper | translate}}</h4>
                </ng-template>
                <ng-template pTemplate="header" let-fields>
                    <tr>
                        <th *ngFor="let field of fields" [pSortableColumn]="field.field"
                            [pTooltip]="field.headerTooltipTranslated" [style.max-width.px]="field.width"
                            [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                            {{field.headerTranslated}}
                            <p-sortIcon [field]="field.field"></p-sortIcon>
                        </th>
                    </tr>
                </ng-template>
                <ng-template pTemplate="body" let-el let-columns="fields">
                    <tr [pSelectableRow]="el">
                        <td *ngFor="let field of fields"
                            [style.max-width.px]="field.width"
                            [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                            <ng-container [ngSwitch]="field.templateName">
                                <ng-container *ngSwitchCase="'icon'">
                                    <svg-icon [name]="getValueByPath(el, field)"
                                              [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
                                </ng-container>
                                <ng-container *ngSwitchDefault>
                                    {{getValueByPath(el, field)}}
                                </ng-container>
                            </ng-container>
                        </td>
                    </tr>
                </ng-template>
            </p-table>
            <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
        </div>

        <udf-metadata-security-edit *ngIf="visibleDialog"
                                    [visibleDialog]="visibleDialog"
                                    [callParam]="callParam"
                                    (closeDialog)="handleCloseDialog($event)">
        </udf-metadata-security-edit>
    `,
    providers: [DialogService]
})
export class UDFMetadataSecurityTableComponent extends TableCrudSupportMenuSecurity<UDFMetadataSecurity> implements OnDestroy {

    callParam: UDFMetadataSecurityParam = new UDFMetadataSecurityParam();

    constructor(private uDFMetadataSecurityService: UDFMetadataSecurityService,
                confirmationService: ConfirmationService,
                messageToastService: MessageToastService,
                activePanelService: ActivePanelService,
                dialogService: DialogService,
                filterService: FilterService,
                translateService: TranslateService,
                gps: GlobalparameterService,
                usersettingsService: UserSettingsService) {
        super(AppSettings.UDF_METADATA_SECURITY, uDFMetadataSecurityService, confirmationService, messageToastService,
            activePanelService, dialogService, filterService, translateService, gps, usersettingsService);

        this.addColumnFeqH(DataType.String, 'uiOrder', true, false);
        this.addColumn(DataType.String, 'description', 'FIELD_DESCRIPTION', true, false);
        this.addColumn(DataType.String, 'descriptionHelp', 'FIELD_DESCRIPTION_HELP', true, false);
        this.addColumnFeqH(DataType.String, 'udfDataType', true, false,
            {translateValues: TranslateValue.NORMAL});
        this.addColumnFeqH(DataType.String, 'fieldSize', true, false);
        this.addColumn(DataType.String, AppSettings.CATEGORY_TYPE, AppSettings.ASSETCLASS.toUpperCase(), true, false,
            {translateValues: TranslateValue.NORMAL});
        this.addColumn(DataType.String, 'specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true, false,
            {translateValues: TranslateValue.NORMAL});
        this.multiSortMeta.push({field: 'uiOrder', order: 1});
        this.prepareTableAndTranslate();
    }

    override prepareCallParam(entity: UDFMetadataSecurity): void {
        this.callParam.excludeUiOrders = this.entityList.filter(m => entity == null
            || entity.uiOrder !== m.uiOrder).map(m => m.uiOrder);
        this.callParam.excludeFieldNames = this.entityList.filter(m => entity == null
            || entity.description !== m.description).map(m => m.description);
        this.callParam.uDFMetadataSecurity = entity;
        console.log(this.callParam);
    }

    readData(): void {
        this.uDFMetadataSecurityService.getAllByIdUser().subscribe(umss => {
            this.entityList = plainToInstance(UDFMetadataSecurity, umss);
            this.createTranslatedValueStoreAndFilterField(this.entityList);
        })
    }

    ngOnDestroy(): void {
        this.activePanelService.destroyPanel(this);
    }
}


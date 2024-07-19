import {TableCrudSupportMenuSecurity} from '../../datashowbase/table.crud.support.menu.security';
import {UDFMetadata, UDFMetadataParam, UDFMetadataSecurity} from '../model/udf.metadata';
import {ConfirmationService, FilterService, SortMeta} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {UserSettingsService} from '../../service/user.settings.service';
import {DeleteService} from '../../datashowbase/delete.service';
import {DataType} from '../../../dynamic-form/models/data.type';
import {TranslateValue} from '../../datashowbase/column.config';
import {Observable} from 'rxjs';
import {plainToInstance} from 'class-transformer';
import {ClassConstructor} from 'class-transformer/types/interfaces';
import {FieldDescriptorInputAndShowExtended} from '../../dynamicfield/field.descriptor.input.and.show';
import {TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';

/**
 * Base class for displaying metadata in a table. The user's own entities can be edited via a further dialog.
 */
export abstract class UDFMetaTable<T extends UDFMetadata> extends TableCrudSupportMenu<T> {

  protected constructor(private classz: ClassConstructor<T>,
    entityName: string,
    private deleteReadAllService: DeleteReadAllService<UDFMetadata>,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {

    super(entityName, deleteReadAllService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService)
  }

  protected abstract addAdditionalFields(beforeOthers: boolean): void;

  protected addMetadataBaseFields(sortMeta: SortMeta[]): void {
    this.addAdditionalFields(true);
    this.addColumnFeqH(DataType.String, 'uiOrder', true, false);
    this.addColumnFeqH(DataType.String, 'udfSpecialType', true, false,
      {translateValues: TranslateValue.NORMAL, width: 80});
    this.addColumn(DataType.String, 'description', 'FIELD_DESCRIPTION', true, false);
    this.addColumn(DataType.String, 'descriptionHelp', 'FIELD_DESCRIPTION_HELP', true, false,
      {width: 150});
    this.addColumnFeqH(DataType.String, 'udfDataType', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'fieldSize', true, false, {width: 60});
    this.addAdditionalFields(false);
    this.multiSortMeta.push(...sortMeta);
    this.prepareTableAndTranslate();
  }

  protected beforeEdit(entity: UDFMetadata, uDFMetadataParam :UDFMetadataParam): void {
    uDFMetadataParam.excludeUiOrders = this.entityList.filter(m => entity == null
      || entity.uiOrder !== m.uiOrder).map(m => m.uiOrder);
    uDFMetadataParam.excludeFieldNames = this.entityList.filter(m => entity == null
      || entity.description !== m.description).map(m => m.description);
  }

  override readData(): void {
    this.deleteReadAllService.getAllByIdUser().subscribe((umsList: T[]) => {
      this.entityList = plainToInstance(this.classz, umsList);
      this.createTranslatedValueStoreAndFilterField(this.entityList);
    })
  }

  protected override hasRightsForUpdateEntity(entity: T): boolean {
    return entity.idUser !== 0;
  }

  protected override hasRightsForDeleteEntity(entity: T): boolean {
    return entity.idUser !== 0;
  }
}

export interface DeleteReadAllService<T extends UDFMetadata> extends DeleteService {
  getAllByIdUser(): Observable<T[]>
}

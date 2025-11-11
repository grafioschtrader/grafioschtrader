import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {DividendSplit} from '../../entities/dividend.split';
import {DividendSplitSvgCreator} from '../../shared/dividendsplit/dividend.split.svg.creator';
import {FilterService} from 'primeng/api';

/**
 * Abstract base class for displaying dividend and split data in table format with SVG icon support.
 * Provides common functionality for dividend and security split table components including
 * icon registration, sorting configuration, and create type visualization.
 */
export abstract class DividendSplitTableBase<S extends DividendSplit> extends TableConfigBase {
  /** Array of dividend or split entities to display in the table */
  data: S[];

  /**
   * Creates a new dividend/split table base with sorting and icon configuration.
   *
   * @param filterService PrimeNG filter service for table filtering capabilities
   * @param usersettingsService Service for persisting user table preferences
   * @param translateService Angular translation service for internationalization
   * @param gps Global parameter service for locale and formatting settings
   * @param iconReg SVG icon registry service for registering dividend/split icons
   * @param keyfield Primary key field name for table row identification
   * @param sortField Default field name for initial table sorting
   * @param groupTitle Translation key for the table group title
   */
  protected constructor(filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              private iconReg: SvgIconRegistryService,
              public keyfield: string, sortField: string,
              public groupTitle: string) {
    super(filterService, usersettingsService, translateService, gps);
    this.multiSortMeta.push({field: sortField, order: -1});
    DividendSplitSvgCreator.registerIcons(this.iconReg);
  }

  /**
   * Retrieves the appropriate SVG icon name for a dividend or split entity's create type.
   *
   * @param entity The dividend or split entity containing create type information
   * @param field Column configuration object (unused but required for interface compatibility)
   * @returns SVG icon name corresponding to the entity's create type
   */
  getCreateTypeIcon(entity: S, field: ColumnConfig): string {
    return DividendSplitSvgCreator.createTypeIconMap[entity.createType];
  }
}

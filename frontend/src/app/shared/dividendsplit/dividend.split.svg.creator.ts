import {CreateType} from '../../entities/dividend.split';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {AppSettings} from '../app.settings';

export class DividendSplitSvgCreator {
  public static createTypeIconMap: { [key: number]: string } = {
    [CreateType.CONNECTOR_CREATED]: 'connector',
    [CreateType.ADD_MODIFIED_USER]: 'edit'
  };
  private static SVG = '.svg';
  private static iconLoadDone = false;


  public static registerIcons(iconReg: SvgIconRegistryService): void {
    if (!DividendSplitSvgCreator.iconLoadDone) {
      for (const [key, iconName] of Object.entries(DividendSplitSvgCreator.createTypeIconMap)) {
        iconReg.loadSvg(AppSettings.PATH_ASSET_ICONS + iconName + DividendSplitSvgCreator.SVG, iconName);
      }
      DividendSplitSvgCreator.iconLoadDone = true;
    }
  }
}

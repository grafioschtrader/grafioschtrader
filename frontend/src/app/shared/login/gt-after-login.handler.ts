import {Injectable} from '@angular/core';
import {AfterLoginHandler} from '../../lib/login/service/after-login.handler';
import {ConfigurationWithLoginGT} from '../../lib/login/component/login.component';
import {GlobalGTSessionNames} from '../global.gt.session.names';
import {BaseSettings} from '../../lib/base.settings';
import moment from 'moment';
import {GlobalSessionNames} from '../../lib/global.session.names';
import {AppSettings} from '../app.settings';

/**
 * Grafioschtrader-specific implementation of post-login handler.
 * Initializes GT-specific session storage values after successful authentication.
 */
@Injectable()
export class GtAfterLoginHandler extends AfterLoginHandler {

  handleAfterLogin(configurationWithLogin: ConfigurationWithLoginGT): void {
    // Set default report date to today
    sessionStorage.setItem(GlobalGTSessionNames.REPORT_UNTIL_DATE, moment().format(BaseSettings.FORMAT_DATE_SHORT_NATIVE));

    // Store supported cryptocurrencies
    sessionStorage.setItem(GlobalGTSessionNames.CRYPTOS, JSON.stringify(configurationWithLogin.cryptocurrencies));

    BaseSettings.resetInterFractionLimit(AppSettings, GlobalSessionNames.STANDARD_CURRENCY_PRECISIONS_AND_LIMITS);
  }
}

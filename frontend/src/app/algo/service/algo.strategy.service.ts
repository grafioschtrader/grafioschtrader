import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {InputAndShowDefinitionStrategy} from '../model/input.and.show.definition.strategy';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {AlgoStrategyImplementations} from '../../shared/types/algo.strategy.implementations';
import {AlgoStrategy} from '../model/algo.strategy';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';

@Injectable()
export class AlgoStrategyService extends AuthServiceWithLogout<AlgoStrategy> implements DeleteService,
  ServiceEntityUpdate<AlgoStrategy> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getUnusedStrategiesForManualAdding(idAlgoAssetclassSecurity: number): Observable<AlgoStrategyImplementations[]> {
    return <Observable<AlgoStrategyImplementations[]>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_STRATEGY_KEY}/unusedsrategies/${idAlgoAssetclassSecurity}`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  getFormDefinitionsByAlgoStrategy(algoStrategyImplementations: AlgoStrategyImplementations): Observable<InputAndShowDefinitionStrategy> {
    return <Observable<InputAndShowDefinitionStrategy>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_STRATEGY_KEY}/form/${algoStrategyImplementations}`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(algoStrategy: AlgoStrategy): Observable<AlgoStrategy> {
    return this.updateEntity(algoStrategy, algoStrategy.idAlgoRuleStrategy, AppSettings.ALGO_STRATEGY_KEY);
  }

  public deleteEntity(idAlgoStrategy: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_STRATEGY_KEY}/${idAlgoStrategy}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}

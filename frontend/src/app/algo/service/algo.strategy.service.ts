import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {InputAndShowDefinitionStrategy} from '../model/input.and.show.definition.strategy';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {AlgoStrategyImplementationType} from '../../shared/types/algo.strategy.implementation.type';
import {AlgoStrategy} from '../model/algo.strategy';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {AlgoLevelType} from '../model/algo.top';

@Injectable()
export class AlgoStrategyService extends AuthServiceWithLogout<AlgoStrategy> implements DeleteService,
  ServiceEntityUpdate<AlgoStrategy> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getStrategiesForLevel(algoLevelType: AlgoLevelType): Observable<AlgoStrategyImplementationType[]> {
    return <Observable<AlgoStrategyImplementationType[]>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_STRATEGY_KEY}/level/${algoLevelType}`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getUnusedStrategiesForManualAdding(idAlgoAssetclassSecurity: number): Observable<AlgoStrategyImplementationType[]> {
    return <Observable<AlgoStrategyImplementationType[]>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.ALGO_STRATEGY_KEY}/unusedsrategies/${idAlgoAssetclassSecurity}`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  getFormDefinitionsByAlgoStrategy(algoStrategyImplementations: AlgoStrategyImplementationType): Observable<InputAndShowDefinitionStrategy> {
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

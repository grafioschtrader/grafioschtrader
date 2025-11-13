import {BaseID} from '../../entities/base.id';

export class ConnectorApiKey implements BaseID {
  idProvider: string = null;
  apiKey: string = null;
  subscriptionType: SubscriptionTypeValue = null;

  public getId(): string {
    return this.idProvider;
  }
}

export interface SubscriptionTypeReadableName {
  readableName: string;
  subscriptionTypes: SubscriptionTypeValue[];
}

export interface ISubscriptionIdentifier {
  readonly id: number;
}

export type SubscriptionTypeValue = number | string | ISubscriptionIdentifier;

import { BaseID } from '../../entities/base.id';

export class ConnectorApiKey implements BaseID {
  idProvider: string | null = null;
  apiKey: string | null = null;
  subscriptionType: SubscriptionTypeValue | null = null;

  public getId(): string | null {
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

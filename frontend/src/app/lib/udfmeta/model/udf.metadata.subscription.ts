import { BaseID } from '../../entities/base.id';

export class UDFMetadataSubscription implements BaseID {
  idUDFMetadataSubscription: number | null = null;
  idUDFMetadata: number | null = null;
  idUser: number | null = null;
  uiOrder: number | null = null;

  getId(): number | null {
    return this.idUDFMetadataSubscription;
  }
}

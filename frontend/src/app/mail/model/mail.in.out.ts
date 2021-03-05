import {BaseID} from '../../entities/base.id';
import {Exclude} from 'class-transformer';

export abstract class MailInOut implements BaseID {
  idMailInOut: number;
  idUserFrom: number;
  idUserTo: number;
  idRoleTo: number;
  subject: string;
  message: string;
  roleName: string;

  @Exclude()
  getId(): number {
    return this.idMailInOut;
  }
}

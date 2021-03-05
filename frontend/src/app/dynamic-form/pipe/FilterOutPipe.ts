import {Pipe, PipeTransform} from '@angular/core';

@Pipe({
  name: 'filterOut'
})
export class FilterOutPipe implements PipeTransform {
  constructor() {
  }

  transform(inputStr: String, filterOutValue: String): any {
    return inputStr === filterOutValue ? null : inputStr;
  }
}

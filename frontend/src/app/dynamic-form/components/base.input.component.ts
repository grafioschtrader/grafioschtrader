import {FieldFormFormGroupConfig} from '../models/field.form.form.group.config';
import { AfterViewInit, ElementRef, OnInit, ViewChild, Directive } from '@angular/core';
import {FieldConfig} from '../models/field.config';
import {FormConfig} from '../models/form.config';
import {FormGroup, Validators} from '@angular/forms';
import {DataType} from '../models/data.type';

@Directive()
export abstract class BaseInputComponent implements FieldFormFormGroupConfig, OnInit, AfterViewInit {

  // Otherwise enum DataType can't be used in a html template
  DataType: typeof DataType = DataType;

  // @ViewChild('input', {read: ElementRef}) el: ElementRef;
  @ViewChild('input') el: ElementRef;

  config: FieldConfig;
  formConfig: FormConfig;
  group: FormGroup;

  isRequired = false;

  ngOnInit(): void {
    this.reEvaluateRequired();
  }

  ngAfterViewInit(): void {
    this.config.elementRef = this.el;
    this.config.baseInputComponent = this;
  }

  reEvaluateRequired(): void {
    this.isRequired = this.config.validation && this.config.validation.indexOf(Validators.required) >= 0;
  }

}

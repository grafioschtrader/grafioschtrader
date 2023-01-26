## Intraday price data
### Possible candidates
### To observe
### Does not work

### Should be implemented 

## Historical price data

### Possible candidates

### To observe
Before it can be implemented, these points still need to be clarified.
### Nordic Nasdaq (Nasdaq: NDAQ)

```
<post>
<param+name="Exchange"+value="NMF"/>
<param+name="SubSystem"+value="History"/>
<param+name="Action"+value="GetDataSeries"/>
<param+name="AppendIntraDay"+value="no"/>
<param+name="FromDate"+value="2000-01-03"/>
<param+name="ToDate"+value="2023-01-16"/>
<param+name="Instrument"+value="SSE160271"/>
<param+name="hi__a"+value="0,5,6,3,1,2,4,21,8,10,12,9,11"/>
<param+name="OmitNoTrade"+value="true"/>
<param+name="ext_xslt_lang"+value="en"/>
<param+name="ext_xslt"+value="/nordicV3/hi_csv.xsl"/>
<param+name="ext_xslt_options"+value=",adjusted,"/>
<param+name="ext_contenttype"+value="application/ms-excel"/>
<param+name="ext_contenttypefilename"+value="NDA-SE-2000-01-03-2023-01-16.csv"/>
<param+name="ext_xslt_hiddenattrs"+value=",iv,ip,"/>
<param+name="ext_xslt_tableId"+value="historicalTable"/>
<param+name="DefaultDecimals"+value="false"/>
<param+name="app"+value="/shares/historicalprices"/>
</post>
`` 

### Does not work
Listed here are those data sources that were considered possible candidates but ultimately did not seem feasible for implementation.

### Should be implemented 

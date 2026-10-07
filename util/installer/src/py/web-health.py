import json,sys; d=json.load(open(sys.argv[1])); sys.exit(d.get("databaseName") != "grafioschtrader" or d.get("activeProfile") not in ("", "production"))

import json,sys; d=json.load(sys.stdin)["dependencies"]; assert d["@angular/cli"]["version"]==sys.argv[1] and d["semver"]["version"]==sys.argv[2]

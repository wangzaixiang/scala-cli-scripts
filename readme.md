# staruml_util

this util is used to generate a staruml model for a database

# usage
```bash
# generate a star uml fragmant
./startuml_dbgen --driver com.mysql.cj.jdbc.Driver --url jdbc:mysql://192.168.239.15:3306/km_fans --user dev_user --password dev666888 --catalog xdd_product_center --outFile /tmp/xdd_product_center.mfj
 
```

# More
using [scala-cli](https://scala-cli.virtuslab.org) to compile/running/package script.

1. run. `scala-cli run staruml_dbgen.scala -- --driver ...`
2. package. `scala-cli package staruml_dbgen.scala -o staruml_dbgen --assembly`

It looks scala-cli provide a more simple way than ammnoite-repl. 

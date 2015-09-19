JPMML-R
=======

Java library for converting R models to PMML.

# Features #

* Fast and memory-efficient:
  * Can produce a 5 GB Random Forest PMML file in less than 1 minute on a desktop PC
* Supported model types:
  * `stats` package:
    * `kmeans` - K-Means clustering
  * [`gbm` package] (http://cran.r-project.org/web/packages/gbm/):
    * `gbm` - Gradient Boosting Machine (GBM) regression and classification
  * [`party` package] (http://cran.r-project.org/web/packages/party/):
    * `ctree` - Conditional Inference Tree (CIT) classification
  * [`randomForest` package] (http://cran.r-project.org/web/packages/randomForest/):
    * `randomForest.formula` ("formula interface") - Random Forest (RF) regression and classification
    * `randomForest` ("matrix interface") - Random Forest regression and classification
  * [`caret` package] (http://cran.r-project.org/web/packages/caret/):
    * `train.formula` ("formula interface") - All supported model types
    * `train` ("matrix interface") - All supported model types
* Production quality:
  * Fully compliant with the [JPMML-Evaluator] (https://github.com/jpmml/jpmml-evaluator) library.

# Prerequisites #

* Java 1.7 or newer.

# Installation #

Enter the project root directory and build using [Apache Maven] (http://maven.apache.org/):
```
mvn clean install
```

The build produces an executable uber-JAR file `target/converter-executable-1.0-SNAPSHOT.jar`.

# Usage #

A typical fast conversion workflow has the following steps:

1. Use R to train a model.
2. Serialize this model in [ProtoBuf data format] (https://code.google.com/p/protobuf/) to a file in local filesystem.
3. Use the JPMML-R converter application to turn this ProtoBuf file to a PMML file.

### The R side of operations

The serialization is handled by the [`RProtoBuf` package] (http://cran.r-project.org/web/packages/RProtoBuf/).

The following R script trains a Random Forest (RF) model and saves it in ProtoBuf data format to a file `rf.pb`:
```R
library("randomForest")
library("RProtoBuf")

data(mydata)

rf = randomForest(target ~ ., data = mydata)

con = file("rf.pb", open = "wb")
serialize_pb(rf, con)
close(con)
```

### The JPMML-R side of operations

Converting the ProtoBuf file `rf.pb` to a PMML file `rf.pmml`:
```
java -jar target/converter-executable-1.0-SNAPSHOT.jar --pb-input rf.pb --pmml-output rf.pmml
```

The conversion of large files (1 GB and beyond) can be sped up by increasing the JVM heap size using `-Xms` and `-Xmx` options:
```
java -Xms4G -Xmx8G -jar target/converter-executable-1.0-SNAPSHOT.jar --pb-input rf.pb --pmml-output rf.pmml
```

# License #

JPMML-R is dual-licensed under the [GNU Affero General Public License (AGPL) version 3.0] (http://www.gnu.org/licenses/agpl-3.0.html) and a commercial license.

# Additional information #

Please contact [info@openscoring.io] (mailto:info@openscoring.io)
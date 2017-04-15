JPMML-R
=======

Java library and command-line application for converting [R](http://www.r-project.org/) models to PMML.

# Features #

* Fast and memory-efficient:
  * Can produce a 5 GB Random Forest PMML file in less than 1 minute on a desktop PC
* Supported model and transformation types:
  * [`caret` package](http://cran.r-project.org/web/packages/caret/):
    * `preProcess` - Transformation methods "range", "center", "scale" and "medianImpute"
    * `train` - Selected JPMML-R model types
  * [`earth` package](https://cran.r-project.org/web/packages/earth/):
    * `earth` - Multivariate Adaptive Regression Spline (MARS) regression
  * [`e1071` package](https://cran.r-project.org/web/packages/e1071/):
    * `svm` - Support Vector Machine (SVM) regression, classification and anomaly detection
  * [`gbm` package](http://cran.r-project.org/web/packages/gbm/):
    * `gbm` - Gradient Boosting Machine (GBM) regression and classification
  * [`IsolationForest` package](https://r-forge.r-project.org/R/?group_id=479):
    * `iForest` - Isolation Forest (IF) anomaly detection
  * [`party` package](http://cran.r-project.org/web/packages/party/):
    * `ctree` - Conditional Inference Tree (CIT) classification
  * [`pls` package](https://cran.r-project.org/web/packages/pls/)
    * `mvr` - Multivariate Regression (MVR) regression
  * [`randomForest` package](http://cran.r-project.org/web/packages/randomForest/):
    * `randomForest` - Random Forest (RF) regression and classification
  * [`ranger` package](https://cran.r-project.org/web/packages/ranger/):
    * `ranger` - Random Forest regression and classification
  * [`rms` package](https://cran.r-project.org/web/packages/rms/):
    * `lrm` - Binary Logistic Regression (LR) classification
    * `ols` - Ordinary Least Squares (OLS) regression
  * [`r2pmml` package](https://github.com/jpmml/r2pmml):
    * `scorecard` - Scorecard regression
  * `stats` package:
    * `glm` - Generalized linear (GLM) regression and classification
    * `kmeans` - K-Means clustering
    * `lm` - Linear (LM) regression
  * [`xgboost` package](https://cran.r-project.org/web/packages/xgboost/):
    * `xgb.Booster` - XGBoost (XGB) regression and classification
* Production quality:
  * Complete test coverage.
  * Fully compliant with the [JPMML-Evaluator](https://github.com/jpmml/jpmml-evaluator) library.

# Prerequisites #

* Java 1.7 or newer.

# Installation #

Enter the project root directory and build using [Apache Maven](http://maven.apache.org/):
```
mvn clean install
```

The build produces an executable uber-JAR file `target/converter-executable-1.2-SNAPSHOT.jar`.

# Usage #

A typical workflow can be summarized as follows:

1. Use R to train a model.
2. Serialize the model in [RDS data format](https://stat.ethz.ch/R-manual/R-devel/library/base/html/readRDS.html) to a file in a local filesystem.
3. Use the JPMML-R command-line converter application to turn the RDS file to a PMML file.

### The R side of operations

The following R script trains a Random Forest (RF) model and saves it in RDS data format to a file `rf.rds`:
```R
library("randomForest")

rf = randomForest(Species ~ ., data = iris)

saveRDS(rf, "rf.rds")
```

### The JPMML-R side of operations

Converting the RDS file `rf.rds` to a PMML file `rf.pmml`:
```
java -jar target/converter-executable-1.2-SNAPSHOT.jar --rds-input rf.rds --pmml-output rf.pmml
```

Getting help:
```
java -jar target/converter-executable-1.2-SNAPSHOT.jar --help
```

The conversion of large files (1 GB and beyond) can be sped up by increasing the JVM heap size using `-Xms` and `-Xmx` options:
```
java -Xms4G -Xmx8G -jar target/converter-executable-1.2-SNAPSHOT.jar --rds-input rf.rds --pmml-output rf.pmml
```

# License #

JPMML-R is licensed under the [GNU Affero General Public License (AGPL) version 3.0](http://www.gnu.org/licenses/agpl-3.0.html). Other licenses are available on request.

# Additional information #

Please contact [info@openscoring.io](mailto:info@openscoring.io)

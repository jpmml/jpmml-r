JPMML-R
=======

Java library and command-line application for converting [R](https://www.r-project.org/) models to PMML.

# Features #

* Fast and memory-efficient:
  * Can produce a 5 GB Random Forest PMML file in less than 1 minute on a desktop PC
* Supported model and transformation types:
  * [`ada` package](https://cran.r-project.org/package=ada):
    * `ada` - Stochastic Boosting (SB) classification
  * [`adabag` package](https://cran.r-project.org/package=adabag):
    * `bagging` - Bagging classification
    * `boosting` - Boosting classification
  * [`caret` package](https://cran.r-project.org/package=caret):
    * `preProcess` - Transformation methods "range", "center", "scale" and "medianImpute"
    * `train` - Selected JPMML-R model types
  * [`caretEnsemble` package](https://cran.r-project.org/package=caretEnsemble):
    * `caretEnsemble` - Ensemble regression and classification
  * [`earth` package](https://cran.r-project.org/package=earth):
    * `earth` - Multivariate Adaptive Regression Spline (MARS) regression
  * [`elmNN` package](https://cran.r-project.org/package=elmNN):
    * `elmNN` - Extreme Learning Machine (ELM) regression
  * [`e1071` package](https://cran.r-project.org/package=e1071):
    * `naiveBayes` - Naive Bayes (NB) classification
    * `svm` - Support Vector Machine (SVM) regression, classification and anomaly detection
  * [`gbm` package](https://cran.r-project.org/package=gbm):
    * `gbm` - Gradient Boosting Machine (GBM) regression and classification
  * [`glmnet` package](https://cran.r-project.org/package=glmnet):
    * `glmnet` (`elnet`, `fishnet`, `lognet` and `multnet` subtypes) - Generalized Linear Model with lasso or elasticnet regularization (GLMNet) regression amd classification
    * `cv.glmnet` - Cross-validated GLMNet regression and calculation
  * [`IsolationForest` package](https://r-forge.r-project.org/R/?group_id=479):
    * `iForest` - Isolation Forest (IF) anomaly detection
  * [`neuralnet` package](https://cran.r-project.org/package=neuralnet):
    * `nn` - Neural Network (NN) regression
  * [`nnet` package](https://cran.r-project.org/package=nnet):
    * `multinom` - Multinomial log-linear classification
    * `nnet.formula` - Neural Network (NNet) regression and classification
  * [`party` package](https://cran.r-project.org/package=party):
    * `ctree` - Conditional Inference Tree (CIT) classification
  * [`pls` package](https://cran.r-project.org/package=pls)
    * `mvr` - Multivariate Regression (MVR) regression
  * [`randomForest` package](https://cran.r-project.org/package=randomForest):
    * `randomForest` - Random Forest (RF) regression and classification
  * [`ranger` package](https://cran.r-project.org/package=ranger):
    * `ranger` - Random Forest (RF) regression and classification
  * [`rms` package](https://cran.r-project.org/package=rms):
    * `lrm` - Binary Logistic Regression (LR) classification
    * `ols` - Ordinary Least Squares (OLS) regression
  * [`rpart` package](https://cran.r-project.org/package=rpart):
    * `rpart` - Recursive Partitioning (RPart) regression and classification
  * [`r2pmml` package](https://github.com/jpmml/r2pmml):
    * `scorecard` - Scorecard regression
  * `stats` package:
    * `glm` - Generalized Linear Model (GLM) regression and classification
    * `kmeans` - K-Means clustering
    * `lm` - Linear Model (LM) regression
  * [`xgboost` package](https://cran.r-project.org/package=xgboost):
    * `xgb.Booster` - XGBoost (XGB) regression and classification
* Production quality:
  * Complete test coverage.
  * Fully compliant with the [JPMML-Evaluator](https://github.com/jpmml/jpmml-evaluator) library.

# Prerequisites #

* Java 1.8 or newer.

# Installation #

Enter the project root directory and build using [Apache Maven](http://maven.apache.org/):
```
mvn clean install
```

The build produces an executable uber-JAR file `target/converter-executable-1.3-SNAPSHOT.jar`.

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
java -jar target/converter-executable-1.3-SNAPSHOT.jar --rds-input rf.rds --pmml-output rf.pmml
```

Getting help:
```
java -jar target/converter-executable-1.3-SNAPSHOT.jar --help
```

The conversion of large files (1 GB and beyond) can be sped up by increasing the JVM heap size using `-Xms` and `-Xmx` options:
```
java -Xms4G -Xmx8G -jar target/converter-executable-1.3-SNAPSHOT.jar --rds-input rf.rds --pmml-output rf.pmml
```

# License #

JPMML-R is dual-licensed under the [GNU Affero General Public License (AGPL) version 3.0](http://www.gnu.org/licenses/agpl-3.0.html), and a commercial license.

# Additional information #

JPMML-R is developed and maintained by Openscoring Ltd, Estonia.

Interested in using JPMML software in your application? Please contact [info@openscoring.io](mailto:info@openscoring.io)

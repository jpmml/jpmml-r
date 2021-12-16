JPMML-R [![Build Status](https://github.com/jpmml/jpmml-r/workflows/maven/badge.svg)](https://github.com/jpmml/jpmml-r/actions?query=workflow%3A%22maven%22)
=======

Java library and command-line application for converting [R](https://www.r-project.org/) models to PMML.

# Table of Contents #

* [Features](#features)
* [Prerequisites](#prerequisites)
* [Installation](#installation)
* [Usage](#usage)
  * [The R side of operations](#the-r-side-of-operations)
  * [The JPMML-R side of operations](#the-jpmml-r-side-of-operations)
* [Documentation](#documentation)
* [License](#license)
* [Additional information](#additional-information)

# Features #

* Fast and memory-efficient:
  * Can produce a 5 GB Random Forest PMML file in less than 1 minute on a desktop PC
* Supported model and transformation types:
  * [`ada`](https://cran.r-project.org/package=ada) package:
    * `ada` - Stochastic Boosting (SB) classification
  * [`adabag`](https://cran.r-project.org/package=adabag) package:
    * `bagging` - Bagging classification
    * `boosting` - Boosting classification
  * [`caret`](https://cran.r-project.org/package=caret) package:
    * `preProcess` - Transformation methods "range", "center", "scale" and "medianImpute"
    * `train` - Selected JPMML-R model types
  * [`caretEnsemble`](https://cran.r-project.org/package=caretEnsemble) package:
    * `caretEnsemble` - Ensemble regression and classification
  * [`CHAID`](https://r-forge.r-project.org/R/?group_id=343) package:
    * `party` - CHi-squared Automated Interaction Detection (CHAID) classification
  * [`earth`](https://cran.r-project.org/package=earth) package:
    * `earth` - Multivariate Adaptive Regression Spline (MARS) regression
  * [`elmNN`](https://cran.r-project.org/package=elmNN) package:
    * `elmNN` - Extreme Learning Machine (ELM) regression
  * [`evtree`](https://cran.r-project.org/package=evtree) package:
    * `party` - Evolutionary Learning of Trees (EvTree) regression and classification
  * [`e1071`](https://cran.r-project.org/package=e1071) package:
    * `naiveBayes` - Naive Bayes (NB) classification
    * `svm` - Support Vector Machine (SVM) regression, classification and anomaly detection
  * [`gbm`](https://cran.r-project.org/package=gbm) package:
    * `gbm` - Gradient Boosting Machine (GBM) regression and classification
  * [`glmnet`](https://cran.r-project.org/package=glmnet) package:
    * `glmnet` (`elnet`, `fishnet`, `lognet` and `multnet` subtypes) - Generalized Linear Model with lasso or elasticnet regularization (GLMNet) regression and classification
    * `cv.glmnet` - Cross-validated GLMNet regression and calculation
  * [`IsolationForest`](https://r-forge.r-project.org/R/?group_id=479) package:
    * `iForest` - Isolation Forest (IF) anomaly detection
  * [`mlr`](https://cran.r-project.org/package=mlr) package:
    * `WrappedModel` - Selected JPMML-R model types.
  * [`neuralnet`](https://cran.r-project.org/package=neuralnet) package:
    * `nn` - Neural Network (NN) regression
  * [`nnet`](https://cran.r-project.org/package=nnet) package:
    * `multinom` - Multinomial log-linear classification
    * `nnet.formula` - Neural Network (NNet) regression and classification
  * [`party`](https://cran.r-project.org/package=party) package:
    * `ctree` - Conditional Inference Tree (CIT) classification
  * [`partykit`](https://cran.r-project.org/package=partykit) package:
    * `party` - Recursive Partytioning (Party) regression and classification
  * [`pls`](https://cran.r-project.org/package=pls) package:
    * `mvr` - Multivariate Regression (MVR) regression
  * [`randomForest`](https://cran.r-project.org/package=randomForest) package:
    * `randomForest` - Random Forest (RF) regression and classification
  * [`ranger`](https://cran.r-project.org/package=ranger) package:
    * `ranger` - Random Forest (RF) regression and classification
  * [`rms`](https://cran.r-project.org/package=rms) package:
    * `lrm` - Binary Logistic Regression (LR) classification
    * `ols` - Ordinary Least Squares (OLS) regression
  * [`rpart`](https://cran.r-project.org/package=rpart) package:
    * `rpart` - Recursive Partitioning (RPart) regression and classification
  * [`r2pmml`](https://github.com/jpmml/r2pmml) package:
    * `scorecard` - Scorecard regression
  * `stats` package:
    * `glm` - Generalized Linear Model (GLM) regression and classification
    * `kmeans` - K-Means clustering
    * `lm` - Linear Model (LM) regression
  * [`xgboost`](https://cran.r-project.org/package=xgboost) package:
    * `xgb.Booster` - XGBoost (XGB) regression and classification
* Data pre-processing using [model formulae](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/formula.html):
  * Interaction terms
  * `base::I(..)` function terms:
    * Logical operators `&`, `|` and `!`
    * Relational operators `==`, `!=`, `<`, `<=`, `>=` and `>`
    * Arithmetic operators `+`, `-`, `*`, `/`, and `%`
    * Exponentiation operators `^` and `**`
    * The `is.na` function
    * Arithmetic functions `abs`, `ceiling`, `exp`, `floor`, `log`, `log10`, `round` and `sqrt`
  * `base::cut()` and `base::ifelse()` function terms
  * `plyr::revalue()` and `plyr::mapvalues()` function terms
* Production quality:
  * Complete test coverage.
  * Fully compliant with the [JPMML-Evaluator](https://github.com/jpmml/jpmml-evaluator) library.

# Prerequisites #

* Java 1.8 or newer.

# Installation #

Enter the project root directory and build using [Apache Maven](https://maven.apache.org/):
```
mvn clean install
```

The build produces an executable uber-JAR file `target/jpmml-r-executable-1.5-SNAPSHOT.jar`.

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

saveRDS(rf, "rf.rds", version = 2)
```

### The JPMML-R side of operations

Converting the RDS file `rf.rds` to a PMML file `rf.pmml`:
```
java -jar target/jpmml-r-executable-1.5-SNAPSHOT.jar --rds-input rf.rds --pmml-output rf.pmml
```

Getting help:
```
java -jar target/jpmml-r-executable-1.5-SNAPSHOT.jar --help
```

The conversion of large files (1 GB and beyond) can be sped up by increasing the JVM heap size using `-Xms` and `-Xmx` options:
```
java -Xms4G -Xmx8G -jar target/jpmml-r-executable-1.5-SNAPSHOT.jar --rds-input rf.rds --pmml-output rf.pmml
```

# Documentation #

Up-to-date:

* [Converting logistic regression models to PMML documents](https://openscoring.io/blog/2020/01/19/converting_logistic_regression_pmml/#r)
* [Deploying R language models on Apache Spark ML](https://openscoring.io/blog/2019/02/09/deploying_rlang_model_sparkml/)

Slightly outdated:

* [Converting R to PMML](https://www.slideshare.net/VilluRuusmann/converting-r-to-pmml-82182483)

# License #

JPMML-R is licensed under the terms and conditions of the [GNU Affero General Public License, Version 3.0](https://www.gnu.org/licenses/agpl-3.0.html).

If you would like to use JPMML-R in a proprietary software project, then it is possible to enter into a licensing agreement which makes JPMML-R available under the terms and conditions of the [BSD 3-Clause License](https://opensource.org/licenses/BSD-3-Clause) instead.

# Additional information #

JPMML-R is developed and maintained by Openscoring Ltd, Estonia.

Interested in using [Java PMML API](https://github.com/jpmml) software in your company? Please contact [info@openscoring.io](mailto:info@openscoring.io)
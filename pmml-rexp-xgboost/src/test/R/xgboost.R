library("r2pmml")
library("xgboost")

source("util.R")

auto = loadAutoCsv("Auto")

auto_X = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

generateXGBoostAuto = function(){
	auto.xgboost = xgboost(x = auto_X, y = auto_y, objective = "reg:squarederror", nrounds = 71)
	auto.xgboost = verify(auto.xgboost, newdata = auto_X[sample(nrow(auto_X), 10), ], response_name = "mpg")

	mpg = predict(auto.xgboost, newdata = auto_X)

	auto.xgboost = decorate(auto.xgboost, fmap = as.fmap(auto_X), response_name = "mpg", compact = TRUE)
	
	storeRds(auto.xgboost, "XGBoostAuto")
	storeCsv(data.frame("mpg" = mpg), "XGBoostAuto")
}

set.seed(42)

generateXGBoostAuto()

audit = loadAuditCsv("AuditNA")
audit$Deductions = NULL

audit_X = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]

# XGBoost treats the first factor level as event
audit_y = factor(audit_y, levels = c("1", "0"))

generateXGBoostAuditNA = function(){
	audit.xgboost = xgboost(x = audit_X, y = audit_y, objective = "binary:logistic", nrounds = 71)
	audit.xgboost = verify(audit.xgboost, newdata = audit_X[sample(nrow(audit_X), 10), ], response_name = "Adjusted", response_levels = c("1", "0"))

	adjusted = predict(audit.xgboost, newdata = audit_X, type = "class")
	prob = predict(audit.xgboost, newdata = audit_X, reshape = TRUE)
	prob = cbind(prob, 1 - prob)

	audit.xgboost = decorate(audit.xgboost, fmap = as.fmap(audit_X), response_name = "Adjusted", response_levels = c("1", "0"), compact = TRUE)
	
	storeRds(audit.xgboost, "XGBoostAuditNA")
	storeCsv(data.frame("Adjusted" = adjusted, "probability(0)" = prob[, 1], "probability(1)" = prob[, 2], check.names = FALSE), "XGBoostAuditNA")
}

set.seed(42)

generateXGBoostAuditNA()

iris = loadIrisCsv("Iris")

iris_X = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

generateXGBoostIris = function(){
	iris.xgboost = xgboost(x = iris_X, y = iris_y, objective = "multi:softprob", nrounds = 15)
	iris.xgboost = verify(iris.xgboost, newdata = iris_X[sample(nrow(iris_X), 10), ], response_name = "Species", response_levels = c("setosa", "versicolor", "virginica"))

	species = predict(iris.xgboost, newdata = iris_X, type = "class")
	prob = predict(iris.xgboost, newdata = iris_X, reshape = TRUE)

	iris.xgboost = decorate(iris.xgboost, fmap = as.fmap(iris_X), response_name = "Species", response_levels = c("setosa", "versicolor", "virginica"), compact = TRUE)
	
	storeRds(iris.xgboost, "XGBoostIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = prob[, 1], "probability(versicolor)" = prob[, 2], "probability(virginica)" = prob[, 3], check.names = FALSE), "XGBoostIris")
}

set.seed(42)

generateXGBoostIris()

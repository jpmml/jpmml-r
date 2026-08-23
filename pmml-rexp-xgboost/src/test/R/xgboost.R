library("r2pmml")
library("xgboost")

source("util.R")

generateXGBoostAudit = function(dataset){
	audit = loadAuditCsv(dataset)
	audit$Deductions = NULL

	audit_X = audit[, -ncol(audit)]
	audit_y = factor(audit[, ncol(audit)], levels = c("0", "1"))

	audit.xgboost = xgboost(x = audit_X, y = audit_y, objective = "binary:logistic", nrounds = 71)
	audit.xgboost = verify(audit.xgboost, newdata = audit_X[sample(nrow(audit_X), 10), ], response_name = "Adjusted", response_levels = c("0", "1"), precision = 1e-5, zeroThreshold = 1e-5)

	adjusted = predict(audit.xgboost, newdata = audit_X, type = "class")
	prob = predict(audit.xgboost, newdata = audit_X, reshape = TRUE)
	prob = cbind(1 - prob, prob)

	audit.xgboost = decorate(audit.xgboost, fmap = as.fmap(audit_X), response_name = "Adjusted", response_levels = c("0", "1"), compact = TRUE)

	storeRds(audit.xgboost, paste("XGBoost", dataset, sep = ""))
	storeCsv(data.frame("Adjusted" = adjusted, "probability(0)" = prob[, 1], "probability(1)" = prob[, 2], check.names = FALSE), paste("XGBoost", dataset, sep = ""))
}

set.seed(42)

generateXGBoostAudit("Audit")
generateXGBoostAudit("AuditNA")

generateXGBoostAuto = function(dataset){
	auto = loadAutoCsv(dataset)

	auto_X = auto[, -ncol(auto)]
	auto_y = auto[, ncol(auto)]

	auto.xgboost = xgboost(x = auto_X, y = auto_y, objective = "reg:squarederror", nrounds = 71)
	auto.xgboost = verify(auto.xgboost, newdata = auto_X[sample(nrow(auto_X), 10), ], response_name = "mpg")

	mpg = predict(auto.xgboost, newdata = auto_X)

	auto.xgboost = decorate(auto.xgboost, fmap = as.fmap(auto_X), response_name = "mpg", compact = TRUE)

	storeRds(auto.xgboost, paste("XGBoost", dataset, sep = ""))
	storeCsv(data.frame("mpg" = mpg), paste("XGBoost", dataset, sep = ""))
}

set.seed(42)

generateXGBoostAuto("Auto")
generateXGBoostAuto("AutoNA")

generateXGBoostIris = function(dataset){
	iris = loadIrisCsv(dataset)

	iris_X = iris[, -ncol(iris)]
	iris_y = iris[, ncol(iris)]

	iris.xgboost = xgboost(x = iris_X, y = iris_y, objective = "multi:softprob", nrounds = 15)
	iris.xgboost = verify(iris.xgboost, newdata = iris_X[sample(nrow(iris_X), 10), ], response_name = "Species", response_levels = c("setosa", "versicolor", "virginica"))

	species = predict(iris.xgboost, newdata = iris_X, type = "class")
	prob = predict(iris.xgboost, newdata = iris_X, reshape = TRUE)

	iris.xgboost = decorate(iris.xgboost, fmap = as.fmap(iris_X), response_name = "Species", response_levels = c("setosa", "versicolor", "virginica"), compact = TRUE)

	storeRds(iris.xgboost, paste("XGBoost", dataset, sep = ""))
	storeCsv(data.frame("Species" = species, "probability(setosa)" = prob[, 1], "probability(versicolor)" = prob[, 2], "probability(virginica)" = prob[, 3], check.names = FALSE), paste("XGBoost", dataset, sep = ""))
}

set.seed(42)

generateXGBoostIris("Iris")

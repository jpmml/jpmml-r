library("r2pmml")
library("xgboost")

source("util.R")

auto = loadAutoCsv("AutoNA")
auto$origin = NULL

auto_X = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

auto.formula = formula(~ . - 1)

auto.frame = model.frame(auto.formula, data = auto_X, na.action = na.pass)
auto.frame.fmap = as.fmap(auto.frame)

auto.contrasts = lapply(auto_X[sapply(auto_X, is.factor)], contrasts, contrasts = FALSE)
auto.matrix = model.matrix(auto.formula, data = auto.frame, contrasts.arg = auto.contrasts)
auto.matrix[is.na(auto.matrix)] = -999

auto.DMatrix = xgb.DMatrix(auto.matrix, label = auto_y, missing = -999)

generateXGBoostAutoNA = function(){
	auto.xgboost = xgboost(data = auto.DMatrix, missing = -999, objective = "reg:linear", nrounds = 71)
	auto.xgboost = decorate(auto.xgboost, fmap = auto.frame.fmap, missing = -999, response_name = "mpg", ntreelimit = 71, compact = TRUE)
	auto.xgboost = verify(auto.xgboost, newdata = auto.matrix[sample(nrow(auto.matrix), 10), ], response_name = "mpg", missing = -999, ntreelimit = 71)

	mpg = predict(auto.xgboost, newdata = auto.DMatrix, missing = -999, ntreelimit = 71)

	storeRds(auto.xgboost, "XGBoostAutoNA")
	storeCsv(data.frame("mpg" = mpg), "XGBoostAutoNA")
}

set.seed(42)

generateXGBoostAutoNA()

audit = loadAuditCsv("Audit")
audit$Deductions = NULL

audit_X = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]

# Convert from factor to integer[0, num_class]
audit_y = as.integer(audit_y) - 1

audit.formula = formula(~ . - 1)

audit.frame = model.frame(audit.formula, data = audit_X)
audit.frame.fmap = as.fmap(audit.frame)

audit.contrasts = lapply(audit_X[sapply(audit_X, is.factor)], contrasts, contrasts = FALSE)
audit.matrix = model.matrix(audit.formula, data = audit.frame, contrasts.arg = audit.contrasts)

audit.DMatrix = xgb.DMatrix(audit.matrix, label = audit_y)

generateXGBoostAudit = function(){
	audit.xgboost = xgboost(data = audit.DMatrix, objective = "binary:logistic", nrounds = 131)
	audit.xgboost = decorate(audit.xgboost, fmap = audit.frame.fmap, response_name = "Adjusted", response_levels = c("0", "1"), ntreelimit = 51, compact = TRUE)
	audit.xgboost = verify(audit.xgboost, newdata = audit.matrix[sample(nrow(audit.matrix), 10), ], response_name = "Adjusted", response_levels = c("0", "1"), ntreelimit = 51)

	prob = predict(audit.xgboost, newdata = audit.DMatrix, ntreelimit = 51)

	storeRds(audit.xgboost, "XGBoostAudit")
	storeCsv(data.frame("Adjusted" = as.integer(prob >= 0.5), "probability(0)" = (1 - prob), "probability(1)" = prob, check.names = FALSE), "XGBoostAudit")
}

set.seed(42)

generateXGBoostAudit()

iris = loadIrisCsv("Iris")

iris_X = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

# Convert from factor to integer[0, num_class]
iris_y = (as.integer(iris_y) - 1)

iris.formula = formula(~ . - 1)

iris.matrix = model.matrix(iris.formula, data = iris_X)
iris.matrix.fmap = as.fmap(iris.matrix)

iris.DMatrix = xgb.DMatrix(iris.matrix, label = iris_y)

generateXGBoostIris = function(){
	iris.xgboost = xgboost(data = iris.DMatrix, missing = NA, objective = "multi:softprob", num_class = 3, nrounds = 15)
	iris.xgboost = decorate(iris.xgboost, fmap = iris.matrix.fmap, response_name = "Species", response_levels = c("setosa", "versicolor", "virginica"), compact = TRUE)
	iris.xgboost = verify(iris.xgboost, newdata = iris.matrix[sample(nrow(iris_X), 10), ], response_name = "Species", response_levels = c("setosa", "versicolor", "virginica"))

	probabilities = predict(iris.xgboost, newdata = iris.DMatrix, reshape = TRUE)
	species = max.col(probabilities)
	species = iris.xgboost$schema$response_levels[species]

	storeRds(iris.xgboost, "XGBoostIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "XGBoostIris")
}

set.seed(42)

generateXGBoostIris()
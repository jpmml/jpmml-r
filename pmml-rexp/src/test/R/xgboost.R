library("Matrix")
library("r2pmml")
library("xgboost")

source("util.R")

auto = loadAutoCsv("Auto")

auto_X = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

auto.formula = formula(~ . - 1)

auto.contrasts = lapply(auto_X[sapply(auto_X, is.factor)], contrasts, contrasts = FALSE)

auto.frame = model.frame(auto.formula, data = auto_X)
auto.frame.fmap = as.fmap(auto.frame)

auto.matrix = model.matrix(auto.formula, data = auto.frame, contrasts.arg = auto.contrasts)

auto.DMatrix = xgb.DMatrix(auto.matrix, label = auto_y)

generateXGBoostAutoNA = function(){
	auto.xgboost = xgboost(data = auto.DMatrix, objective = "reg:linear", nrounds = 71)
	auto.xgboost = decorate(auto.xgboost, fmap = auto.frame.fmap, response_name = "mpg", ntreelimit = 17, compact = TRUE)
	auto.xgboost = verify(auto.xgboost, newdata = auto.matrix[sample(nrow(auto.matrix), 10), ], response_name = "mpg", ntreelimit = 17)

	mpg = predict(auto.xgboost, newdata = auto.DMatrix, ntreelimit = 17)

	storeRds(auto.xgboost, "XGBoostAuto")
	storeCsv(data.frame("mpg" = mpg), "XGBoostAuto")
}

set.seed(42)

generateXGBoostAutoNA()

audit = loadAuditCsv("AuditNA")
audit$Deductions = NULL

audit_X = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]

# Convert from factor to integer[0, num_class]
audit_y = as.integer(audit_y) - 1

audit.formula = formula(~ . - 1)

audit.contrasts = lapply(audit_X[sapply(audit_X, is.factor)], contrasts, contrasts = FALSE)

audit.frame = model.frame(audit.formula, data = audit_X, na.action = na.pass)
audit.frame.fmap = as.fmap(audit.frame)

audit.matrix = model.matrix(audit.formula, data = audit.frame, contrasts.arg = audit.contrasts)
audit.matrix = Matrix(audit.matrix, sparse = TRUE)

generateXGBoostAuditNA = function(){
	audit.xgboost = xgboost(data = audit.matrix, label = audit_y, missing = NA, objective = "binary:logistic", nrounds = 71)
	audit.xgboost = decorate(audit.xgboost, fmap = audit.frame.fmap, response_name = "Adjusted", response_levels = c("0", "1"), ntreelimit = 31, compact = TRUE)
	audit.xgboost = verify(audit.xgboost, newdata = audit.matrix[sample(nrow(audit.matrix), 10), ], response_name = "Adjusted", response_levels = c("0", "1"), ntreelimit = 31)

	prob = predict(audit.xgboost, newdata = audit.matrix, missing = NA, ntreelimit = 31)

	storeRds(audit.xgboost, "XGBoostAuditNA")
	storeCsv(data.frame("Adjusted" = as.integer(prob >= 0.5), "probability(0)" = (1 - prob), "probability(1)" = prob, check.names = FALSE), "XGBoostAuditNA")
}

set.seed(42)

generateXGBoostAuditNA()

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
	iris.xgboost = xgboost(data = iris.DMatrix, objective = "multi:softprob", num_class = 3, nrounds = 15)
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
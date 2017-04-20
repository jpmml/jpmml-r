library("r2pmml")
library("xgboost")

source("util.R")

auto = loadAutoCsv("AutoNA")
auto[is.na(auto)] = -999

auto_X = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

auto.fmap = genFMap(auto_X)
auto.dmatrix = genDMatrix(auto_y, auto_X)

generateXGBoostAutoNA = function(){
	schema = list()
	schema$response_name = "mpg"
	schema$missing = -999

	auto.xgboost = xgboost(data = auto.dmatrix, missing = -999, objective = "reg:linear", nrounds = 15)
	auto.xgboost$fmap = auto.fmap
	auto.xgboost$schema = schema

	mpg = predict(auto.xgboost, newdata = auto.dmatrix, missing = -999)

	storeRds(auto.xgboost, "XGBoostAutoNA")
	storeCsv(data.frame("mpg" = mpg), "XGBoostAutoNA")
}

set.seed(42)

generateXGBoostAutoNA()

iris = loadIrisCsv("Iris")

iris_X = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

# Convert from factor to integer[0, num_class]
iris_y = (as.integer(iris_y) - 1)

iris.fmap = genFMap(iris_X)
iris.dmatrix = genDMatrix(iris_y, iris_X)

generateXGBoostIris = function(){
	schema = list()
	schema$response_name = "Species"
	schema$response_levels = c("setosa", "versicolor", "virginica")

	iris.xgboost = xgboost(data = iris.dmatrix, missing = NA, objective = "multi:softprob", num_class = 3, nrounds = 15)
	iris.xgboost$fmap = iris.fmap
	iris.xgboost$schema = schema

	probabilities = predict(iris.xgboost, newdata = iris.dmatrix)
	probabilities = matrix(probabilities, ncol = 3, byrow = TRUE)
	species = max.col(probabilities)
	species = schema$response_levels[species]

	storeRds(iris.xgboost, "XGBoostIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "XGBoostIris")
}

set.seed(42)

generateXGBoostIris()

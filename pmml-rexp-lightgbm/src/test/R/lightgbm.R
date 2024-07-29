library("lightgbm")

source("util.R")

auto = loadAutoCsv("Auto")

auto_X = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

auto.matrix = as.matrix(auto_X)

auto.dset = lgb.Dataset(auto.matrix, label = auto_y)

generateLightGBMAuto = function(){
	auto.lgbm = lgb.train(params = list(objective = "regression"), data = auto.dset, nrounds = 31)

	mpg = predict(auto.lgbm, auto.matrix)

	storeRds(auto.lgbm, "LightGBMAuto")
	storeCsv(data.frame("_target" = mpg), "LightGBMAuto")
}

set.seed(42)

generateLightGBMAuto()

iris = loadIrisCsv("Iris")

iris_X = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

# Convert from factor to integer[0, num_class]
iris_y = (as.integer(iris_y) - 1)

iris.matrix = as.matrix(iris_X)

iris.dset = lgb.Dataset(iris.matrix, label = iris_y)

generateLightGBMIris = function(){
	iris.lgbm = lgb.train(params = list(objective = "multiclass", num_class = 3, max_depth = 3), data = iris.dset, nrounds = 5)

	probabilities = predict(iris.lgbm, iris.matrix)
	species = max.col(probabilities) - 1

	storeRds(iris.lgbm, "LightGBMIris")
	storeCsv(data.frame("_target" = species, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], "probability(2)" = probabilities[, 3], check.names = FALSE), "LightGBMIris")
}

set.seed(42)

generateLightGBMIris()

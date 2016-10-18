library("xgboost")

source("util.R")

auto = loadAutoCsv("AutoNA")
auto$origin = NULL

auto[is.na(auto)] = -999

auto_x = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

auto_fmap = data.frame(
	"id" = seq(from = 0, (to = ncol(auto_x) - 1)),
	"name" = c("cylinders", "displacement", "horsepower", "weight", "acceleration", "model_year"),
	"type" = c("int", "q", "int", "int", "q", "int")
)

generateXGBoostAutoNA = function(){
	schema = list()
	schema$response_name = "mpg"
	schema$missing = -999

	auto.xgboost = xgboost(data = as.matrix(auto_x), label = auto_y, missing = -999, objective = "reg:linear", nrounds = 15)
	auto.xgboost$fmap = auto_fmap
	auto.xgboost$schema = schema

	mpg = predict(auto.xgboost, newdata = as.matrix(auto_x), missing = -999)

	storeRds(auto.xgboost, "XGBoostAutoNA")
	storeCsv(data.frame("mpg" = mpg), "XGBoostAutoNA")
}

set.seed(42)

generateXGBoostAutoNA()

iris = loadIrisCsv("Iris")

iris_x = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

# Convert from factor to integer[0, num_class]
iris_y = (as.integer(iris_y) - 1)

iris_fmap = data.frame(
	"id" = seq(from = 0, (to = ncol(iris_x) - 1)),
	"name" = c("Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width"),
	"type" = c("q", "q", "q", "q")
)

generateXGBoostIris = function(){
	schema = list()
	schema$response_name = "Species"
	schema$response_levels = c("setosa", "versicolor", "virginica")

	iris.xgboost = xgboost(data = as.matrix(iris_x), label = iris_y, missing = NA, objective = "multi:softprob", num_class = 3, nrounds = 15)
	iris.xgboost$fmap = iris_fmap
	iris.xgboost$schema = schema

	prob = predict(iris.xgboost, newdata = as.matrix(iris_x))
	prob = matrix(prob, ncol = 3, byrow = TRUE)
	species = max.col(prob)
	species = schema$response_levels[species]

	storeRds(iris.xgboost, "XGBoostIris")
	storeCsv(data.frame("Species" = species, "probability_setosa" = prob[, 1], "probability_versicolor" = prob[, 2], "probability_virginica" = prob[, 3]), "XGBoostIris")
}

set.seed(42)

generateXGBoostIris()
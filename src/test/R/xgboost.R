library("xgboost")

source("util.R")

auto = loadAutoCsv("AutoNA")
auto$origin = NULL

auto_x = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

auto_fmap = data.frame(
	"id" = seq(from = 0, (to = ncol(auto_x) - 1)),
	"name" = c("cylinders", "displacement", "horsepower", "weight", "acceleration", "model_year"),
	"type" = c("int", "q", "int", "int", "q", "int")
)

generateXGBoostAutoNA = function(){
	auto.xgboost = xgboost(data = as.matrix(auto_x), label = auto_y, missing = NA, objective = "reg:linear", nrounds = 15)
	auto.xgboost$fmap = auto_fmap

	mpg = predict(auto.xgboost, newdata = as.matrix(auto_x), missing = NA)

	storeRds(auto.xgboost, "XGBoostAutoNA")
	storeCsv(data.frame("_target" = mpg), "XGBoostAutoNA")
}

set.seed(42)

generateXGBoostAutoNA()

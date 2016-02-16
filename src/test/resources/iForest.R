# install.packages("IsolationForest", repos="http://R-Forge.R-project.org")
library("IsolationForest")

source("util.R")

predictIsolationForest = function(iForest, data){
	pred = AnomalyScore(data, iForest)

	result = data.frame("pathLength" = pred$pathLength, "anomalyScore" = pred$outF)

	return (result)
}

auto = loadAutoCsv("csv/Auto.csv")

auto_x = auto[, -ncol(auto)]
auto_x$origin = NULL

generateIsolationForestAuto = function(){
	auto.iForest = IsolationTrees(auto_x, ntree = 7)

	storeRds(auto.iForest, "rds/IsolationForestAuto.rds")
	storeCsv(predictIsolationForest(auto.iForest, auto_x), "csv/IsolationForestAuto.csv")
}

set.seed(42)

generateIsolationForestAuto()

iris = loadIrisCsv("csv/Iris.csv")

iris_x = iris[, -ncol(iris)]

generateIsolationForestIris = function(){
	iris.iForest = IsolationTrees(iris_x, ntree = 7)

	storeRds(iris.iForest, "rds/IsolationForestIris.rds")
	storeCsv(predictIsolationForest(iris.iForest, iris_x), "csv/IsolationForestIris.csv")
}

set.seed(42)

generateIsolationForestIris()

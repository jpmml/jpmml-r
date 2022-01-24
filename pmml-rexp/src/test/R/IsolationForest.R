# install.packages("IsolationForest", repos="http://R-Forge.R-project.org")
library("IsolationForest")

source("util.R")

predictIForest = function(iForest, data){
	pred = AnomalyScore(data, iForest)

	result = data.frame("pathLength" = pred$pathLength, "anomalyScore" = pred$outF)

	return (result)
}

auto = loadAutoCsv("Auto")

auto_x = auto[, -ncol(auto)]
auto_x$origin = NULL

generateIForestAuto = function(){
	auto.iForest = IsolationTrees(auto_x, ntree = 7)

	storeRds(auto.iForest, "IForestAuto")
	storeCsv(predictIForest(auto.iForest, auto_x), "IForestAuto")
}

set.seed(42)

generateIForestAuto()

iris = loadIrisCsv("Iris")

iris_x = iris[, -ncol(iris)]

generateIForestIris = function(){
	iris.iForest = IsolationTrees(iris_x, ntree = 7)

	storeRds(iris.iForest, "IForestIris")
	storeCsv(predictIForest(iris.iForest, iris_x), "IForestIris")
}

set.seed(42)

generateIForestIris()

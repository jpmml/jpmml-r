library("caret")
library("randomForest")

source("util.R")

audit = loadAuditCsv("csv/Audit.csv")

audit_x = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]

predictRandomForestAudit = function(audit.randomForest, data, targetName){
	adjusted = predict(audit.randomForest, newdata = data)
	probabilities = predict(audit.randomForest, newdata = data, type = "prob")

	result = data.frame("y" = adjusted, "probability_0" = probabilities[, 1], "probability_1" = probabilities[, 2])
	names(result) = gsub("^y$", targetName, names(result))

	return (result)
}

generateRandomForestFormulaAudit = function(){
	audit.randomForest = randomForest(Adjusted ~ ., data = audit, ntree = 7)
	print(audit.randomForest)

	storeRds(audit.randomForest, "rds/RandomForestFormulaAudit.rds")
	storeCsv(predictRandomForestAudit(audit.randomForest, audit, "Adjusted"), "csv/RandomForestFormulaAudit.csv")
}

generateRandomForestAudit = function(){
	audit.randomForest = randomForest(x = audit_x, y = audit_y, ntree = 7)
	print(audit.randomForest)

	storeRds(audit.randomForest, "rds/RandomForestAudit.rds")
	storeCsv(predictRandomForestAudit(audit.randomForest, audit_x, "_target"), "csv/RandomForestAudit.csv")
}

set.seed(42)

generateRandomForestFormulaAudit()
generateRandomForestAudit()

generateTrainRandomForestFormulaAuditMatrix = function(){
	audit.train = train(Adjusted ~ ., data = audit, method = "rf", ntree = 7)
	print(audit.train)

	adjusted = predict(audit.train, newdata = audit)

	storeRds(audit.train, "rds/TrainRandomForestFormulaAuditMatrix.rds")
	storeCsv(data.frame("_target" = adjusted), "csv/TrainRandomForestFormulaAuditMatrix.csv")
}

generateTrainRandomForestAudit = function(){
	audit.train = train(x = audit_x, y = audit_y, method = "rf", ntree = 7)
	print(audit.train)

	adjusted = predict(audit.train, newdata = audit_x)

	storeRds(audit.train, "rds/TrainRandomForestAudit.rds")
	storeCsv(data.frame("_target" = adjusted), "csv/TrainRandomForestAudit.csv")
}

set.seed(42)

generateTrainRandomForestFormulaAuditMatrix()
generateTrainRandomForestAudit()

auto = loadAutoCsv("csv/Auto.csv")

auto_x = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

generateRandomForestFormulaAuto = function(){
	auto.randomForest = randomForest(mpg ~ ., data = auto, ntree = 7)
	print(auto.randomForest)

	mpg = predict(auto.randomForest, newdata = auto)

	storeRds(auto.randomForest, "rds/RandomForestFormulaAuto.rds")
	storeCsv(data.frame("mpg" = mpg), "csv/RandomForestFormulaAuto.csv")
}

generateRandomForestAuto = function(){
	auto.randomForest = randomForest(x = auto_x, y = auto_y, ntree = 7)
	print(auto.randomForest)

	mpg = predict(auto.randomForest, newdata = auto_x)

	storeRds(auto.randomForest, "rds/RandomForestAuto.rds")
	storeCsv(data.frame("_target" = mpg), "csv/RandomForestAuto.csv")
}

set.seed(42)

generateRandomForestFormulaAuto()
generateRandomForestAuto()

auto.caret = auto
auto.caret$origin = as.integer(auto.caret$origin)

generateTrainRandomForestFormulaAuto = function(){
	auto.train = train(mpg ~ ., data = auto.caret, method = "rf", ntree = 7)
	print(auto.train)

	mpg = predict(auto.train, newdata = auto.caret)

	storeRds(auto.train, "rds/TrainRandomForestFormulaAuto.rds")
	storeCsv(data.frame("_target" = mpg), "csv/TrainRandomForestFormulaAuto.csv")
}

generateTrainRandomForestAuto = function(){
	auto.train = train(x = auto_x, y = auto_y, method = "rf", ntree = 7)
	print(auto.train)

	mpg = predict(auto.train, newdata = auto_x)

	storeRds(auto.train, "rds/TrainRandomForestAuto.rds")
	storeCsv(data.frame("_target" = mpg), "csv/TrainRandomForestAuto.csv")
}

set.seed(42)

generateTrainRandomForestFormulaAuto()
generateTrainRandomForestAuto()

iris = loadIrisCsv("csv/Iris.csv")

iris_x = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

predictRandomForestIris = function(iris.randomForest, data, targetName){
	species = predict(iris.randomForest, newdata = data)
	probabilities = predict(iris.randomForest, newdata = data, type = "prob")

	result = data.frame("y" = species, "probability_setosa" = probabilities[, 1], "probability_versicolor" = probabilities[, 2], "probability_virginica" = probabilities[, 3])
	names(result) = gsub("^y$", targetName, names(result))

	return (result)
}

generateRandomForestFormulaIris = function(){
	iris.randomForest = randomForest(Species ~ ., data = iris, ntree = 7)
	print(iris.randomForest)

	storeRds(iris.randomForest, "rds/RandomForestFormulaIris.rds")
	storeCsv(predictRandomForestIris(iris.randomForest, iris, "Species"), "csv/RandomForestFormulaIris.csv")
}

generateRandomForestCustFormulaIris = function(){
	iris.randomForest = randomForest(Species ~ . - Sepal.Length, data = iris, ntree = 7)
	print(iris.randomForest)

	storeRds(iris.randomForest, "rds/RandomForestCustFormulaIris.rds")
	storeCsv(predictRandomForestIris(iris.randomForest, iris, "Species"), "csv/RandomForestCustFormulaIris.csv")
}

generateRandomForestIris = function(){
	iris.randomForest = randomForest(x = iris_x, y = iris_y, ntree = 7)
	print(iris.randomForest)

	storeRds(iris.randomForest, "rds/RandomForestIris.rds")
	storeCsv(predictRandomForestIris(iris.randomForest, iris_x, "_target"), "csv/RandomForestIris.csv")
}

set.seed(42)

generateRandomForestFormulaIris()
generateRandomForestCustFormulaIris()
generateRandomForestIris()

wine_quality = loadWineQualityCsv("csv/WineQuality.csv")

wine_quality_x = wine_quality[, -ncol(wine_quality)]
wine_quality_y = wine_quality[, ncol(wine_quality)]

generateRandomForestFormulaWineQuality = function(){
	wine_quality.randomForest = randomForest(quality ~ ., data = wine_quality, ntree = 7)
	print(wine_quality.randomForest)

	quality = predict(wine_quality.randomForest, newdata = wine_quality)

	storeRds(wine_quality.randomForest, "rds/RandomForestFormulaWineQuality.rds")
	storeCsv(data.frame("quality" = quality), "csv/RandomForestFormulaWineQuality.csv")
}

generateRandomForestWineQuality = function(){
	wine_quality.randomForest = randomForest(x = wine_quality_x, y = wine_quality_y, ntree = 7)
	print(wine_quality.randomForest)

	quality = predict(wine_quality.randomForest, newdata = wine_quality_x)

	storeRds(wine_quality.randomForest, "rds/RandomForestWineQuality.rds")
	storeCsv(data.frame("_target" = quality), "csv/RandomForestWineQuality.csv")
}

set.seed(42)

generateRandomForestFormulaWineQuality()
generateRandomForestWineQuality()

wine_color = loadWineColorCsv("csv/WineColor.csv")

wine_color_x = wine_color[, -ncol(wine_color)]
wine_color_y = wine_color[, ncol(wine_color)]

predictRandomForestWineColor = function(wine_color.randomForest, data, targetName){
	color = predict(wine_color.randomForest, newdata = wine_color)
	probabilities = predict(wine_color.randomForest, newdata = wine_color, type = "prob")

	result = data.frame("y" = color, "probability_red" = probabilities[, 1], "probability_white" = probabilities[, 2])
	names(result) = gsub("^y$", targetName, names(result))

	return (result)
}

generateRandomForestFormulaWineColor = function(){
	wine_color.randomForest = randomForest(color ~ ., data = wine_color, ntree = 7)
	print(wine_color.randomForest)

	storeRds(wine_color.randomForest, "rds/RandomForestFormulaWineColor.rds")
	storeCsv(predictRandomForestWineColor(wine_color.randomForest, wine_color, "color"), "csv/RandomForestFormulaWineColor.csv")
}

generateRandomForestWineColor = function(){
	wine_color.randomForest = randomForest(x = wine_color_x, y = wine_color_y, ntree = 7)
	print(wine_color.randomForest)

	storeRds(wine_color.randomForest, "rds/RandomForestWineColor.rds")
	storeCsv(predictRandomForestWineColor(wine_color.randomForest, wine_color_x, "_target"), "csv/RandomForestWineColor.csv")
}

set.seed(42)

generateRandomForestFormulaWineColor()
generateRandomForestWineColor()

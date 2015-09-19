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

	storeProtoBuf(audit.randomForest, "pb/RandomForestFormulaAudit.pb")
	storeCsv(predictRandomForestAudit(audit.randomForest, audit, "Adjusted"), "csv/RandomForestFormulaAudit.csv")
}

generateRandomForestAudit = function(){
	audit.randomForest = randomForest(x = audit_x, y = audit_y, ntree = 7)
	print(audit.randomForest)

	storeProtoBuf(audit.randomForest, "pb/RandomForestAudit.pb")
	storeCsv(predictRandomForestAudit(audit.randomForest, audit_x, "_target"), "csv/RandomForestAudit.csv")
}

set.seed(42)

generateRandomForestFormulaAudit()
generateRandomForestAudit()

generateTrainRandomForestFormulaAuditMatrix = function(){
	audit.train = train(Adjusted ~ ., data = audit, method = "rf", ntree = 7)
	print(audit.train)

	adjusted = predict(audit.train, newdata = audit)

	storeProtoBuf(audit.train, "pb/TrainRandomForestFormulaAuditMatrix.pb")
	storeCsv(data.frame("_target" = adjusted), "csv/TrainRandomForestFormulaAuditMatrix.csv")
}

generateTrainRandomForestAudit = function(){
	audit.train = train(x = audit_x, y = audit_y, method = "rf", ntree = 7)
	print(audit.train)

	adjusted = predict(audit.train, newdata = audit_x)

	storeProtoBuf(audit.train, "pb/TrainRandomForestAudit.pb")
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

	storeProtoBuf(auto.randomForest, "pb/RandomForestFormulaAuto.pb")
	storeCsv(data.frame("mpg" = mpg), "csv/RandomForestFormulaAuto.csv")
}

generateRandomForestAuto = function(){
	auto.randomForest = randomForest(x = auto_x, y = auto_y, ntree = 7)
	print(auto.randomForest)

	mpg = predict(auto.randomForest, newdata = auto_x)

	storeProtoBuf(auto.randomForest, "pb/RandomForestAuto.pb")
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

	storeProtoBuf(auto.train, "pb/TrainRandomForestFormulaAuto.pb")
	storeCsv(data.frame("_target" = mpg), "csv/TrainRandomForestFormulaAuto.csv")
}

generateTrainRandomForestAuto = function(){
	auto.train = train(x = auto_x, y = auto_y, method = "rf", ntree = 7)
	print(auto.train)

	mpg = predict(auto.train, newdata = auto_x)

	storeProtoBuf(auto.train, "pb/TrainRandomForestAuto.pb")
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

	storeProtoBuf(iris.randomForest, "pb/RandomForestFormulaIris.pb")
	storeCsv(predictRandomForestIris(iris.randomForest, iris, "Species"), "csv/RandomForestFormulaIris.csv")
}

generateRandomForestIris = function(){
	iris.randomForest = randomForest(x = iris_x, y = iris_y, ntree = 7)
	print(iris.randomForest)

	storeProtoBuf(iris.randomForest, "pb/RandomForestIris.pb")
	storeCsv(predictRandomForestIris(iris.randomForest, iris_x, "_target"), "csv/RandomForestIris.csv")
}

set.seed(42)

generateRandomForestFormulaIris()
generateRandomForestIris()

wine_quality = loadWineQualityCsv("csv/WineQuality.csv")

wine_quality_x = wine_quality[, -ncol(wine_quality)]
wine_quality_y = wine_quality[, ncol(wine_quality)]

generateRandomForestFormulaWineQuality = function(){
	wine_quality.randomForest = randomForest(quality ~ ., data = wine_quality, ntree = 7)
	print(wine_quality.randomForest)

	quality = predict(wine_quality.randomForest, newdata = wine_quality)

	storeProtoBuf(wine_quality.randomForest, "pb/RandomForestFormulaWineQuality.pb")
	storeCsv(data.frame("quality" = quality), "csv/RandomForestFormulaWineQuality.csv")
}

generateRandomForestWineQuality = function(){
	wine_quality.randomForest = randomForest(x = wine_quality_x, y = wine_quality_y, ntree = 7)
	print(wine_quality.randomForest)

	quality = predict(wine_quality.randomForest, newdata = wine_quality_x)

	storeProtoBuf(wine_quality.randomForest, "pb/RandomForestWineQuality.pb")
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

	storeProtoBuf(wine_color.randomForest, "pb/RandomForestFormulaWineColor.pb")
	storeCsv(predictRandomForestWineColor(wine_color.randomForest, wine_color, "color"), "csv/RandomForestFormulaWineColor.csv")
}

generateRandomForestWineColor = function(){
	wine_color.randomForest = randomForest(x = wine_color_x, y = wine_color_y, ntree = 7)
	print(wine_color.randomForest)

	storeProtoBuf(wine_color.randomForest, "pb/RandomForestWineColor.pb")
	storeCsv(predictRandomForestWineColor(wine_color.randomForest, wine_color_x, "_target"), "csv/RandomForestWineColor.csv")
}

set.seed(42)

generateRandomForestFormulaWineColor()
generateRandomForestWineColor()
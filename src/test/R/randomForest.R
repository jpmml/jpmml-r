library("caret")
library("plyr")
library("randomForest")
library("r2pmml")

source("util.R")

audit = loadAuditCsv("Audit")

audit_x = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]

predictRandomForestAudit = function(audit.randomForest, data, targetName){
	adjusted = predict(audit.randomForest, newdata = data)
	probabilities = predict(audit.randomForest, newdata = data, type = "prob")

	result = data.frame(adjusted)
	names(result) = c(targetName)
	result = data.frame(result, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE)

	return (result)
}

generateRandomForestFormulaAudit = function(){
	audit.randomForest = randomForest(Adjusted ~ ., data = audit, ntree = 71)
	audit.randomForest = decorate(audit.randomForest, compact = TRUE)
	print(audit.randomForest)

	storeRds(audit.randomForest, "RandomForestFormulaAudit")
	storeCsv(predictRandomForestAudit(audit.randomForest, audit, "Adjusted"), "RandomForestFormulaAudit")
}

generateRandomForestCustFormulaAudit = function(){
	audit.randomForest = randomForest(Adjusted ~ . - Education + plyr::revalue(Education, c(Yr1t4 = "Yr1t6", Yr5t6 = "Yr1t6", Yr7t8 = "Yr7t9", Yr9 = "Yr7t9", Yr10 = "Yr10t12", Yr11 = "Yr10t12", Yr12 = "Yr10t12")) - Income + base::cut(Income, breaks = c(100, 1000, 10000, 100000, 1000000)), data = audit, ntree = 71)
	print(audit.randomForest)

	storeRds(audit.randomForest, "RandomForestCustFormulaAudit")
	storeCsv(predictRandomForestAudit(audit.randomForest, audit, "Adjusted"), "RandomForestCustFormulaAudit")
}

generateRandomForestAudit = function(){
	audit.randomForest = randomForest(x = audit_x, y = audit_y, ntree = 71)
	audit.randomForest = decorate(audit.randomForest, compact = TRUE)
	print(audit.randomForest)

	storeRds(audit.randomForest, "RandomForestAudit")
	storeCsv(predictRandomForestAudit(audit.randomForest, audit_x, "_target"), "RandomForestAudit")
}

set.seed(42)

generateRandomForestFormulaAudit()
generateRandomForestCustFormulaAudit()
generateRandomForestAudit()

generateTrainRandomForestFormulaAuditMatrix = function(){
	audit.train = train(Adjusted ~ ., data = audit, method = "rf", ntree = 31)
	print(audit.train)

	adjusted = predict(audit.train, newdata = audit)
	probabilities = predict(audit.train, newdata = audit, type = "prob")

	storeRds(audit.train, "TrainRandomForestFormulaAuditMatrix")
	storeCsv(data.frame("_target" = adjusted, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE), "TrainRandomForestFormulaAuditMatrix")
}

generateTrainRandomForestAudit = function(){
	audit.train = train(x = audit_x, y = audit_y, method = "rf", ntree = 31)
	print(audit.train)

	adjusted = predict(audit.train, newdata = audit_x)
	probabilities = predict(audit.train, newdata = audit_x, type = "prob")

	storeRds(audit.train, "TrainRandomForestAudit")
	storeCsv(data.frame("_target" = adjusted, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE), "TrainRandomForestAudit")
}

set.seed(42)

generateTrainRandomForestFormulaAuditMatrix()
generateTrainRandomForestAudit()

auto = loadAutoCsv("Auto")

auto_x = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

generateRandomForestFormulaAuto = function(){
	auto.randomForest = randomForest(mpg ~ ., data = auto, ntree = 31)
	auto.randomForest = decorate(auto.randomForest, compact = TRUE)
	print(auto.randomForest)

	mpg = predict(auto.randomForest, newdata = auto)

	storeRds(auto.randomForest, "RandomForestFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "RandomForestFormulaAuto")
}

generateRandomForestCustFormulaAuto = function(){
	auto.randomForest = randomForest(mpg ~ I(displacement / cylinders) + . - weight + I(log(weight)) + I(weight ^ 2) + I(weight ^ 3) - horsepower + base::cut(horsepower, breaks = c(20, 40, 60, 80, 100, 150, 200, 400)) - origin + plyr::mapvalues(origin, from = c(1, 2, 3), to = c("US", "Non-US", "Non-US")), data = auto, ntree = 31)
	print(auto.randomForest)

	mpg = predict(auto.randomForest, newdata = auto)

	storeRds(auto.randomForest, "RandomForestCustFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "RandomForestCustFormulaAuto")
}

generateRandomForestAuto = function(){
	auto.randomForest = randomForest(x = auto_x, y = auto_y, ntree = 31)
	auto.randomForest = decorate(auto.randomForest, compact = TRUE)
	print(auto.randomForest)

	mpg = predict(auto.randomForest, newdata = auto_x)

	storeRds(auto.randomForest, "RandomForestAuto")
	storeCsv(data.frame("_target" = mpg), "RandomForestAuto")
}

set.seed(42)

generateRandomForestFormulaAuto()
generateRandomForestCustFormulaAuto()
generateRandomForestAuto()

auto.caret = auto
auto.caret$origin = as.integer(auto.caret$origin)

generateTrainRandomForestFormulaAuto = function(){
	auto.train = train(mpg ~ ., data = auto.caret, method = "rf", ntree = 11)
	print(auto.train)

	mpg = predict(auto.train, newdata = auto.caret)

	storeRds(auto.train, "TrainRandomForestFormulaAuto")
	storeCsv(data.frame("_target" = mpg), "TrainRandomForestFormulaAuto")
}

generateTrainRandomForestAuto = function(){
	auto.train = train(x = auto_x, y = auto_y, method = "rf", ntree = 11)
	print(auto.train)

	mpg = predict(auto.train, newdata = auto_x)

	storeRds(auto.train, "TrainRandomForestAuto")
	storeCsv(data.frame("_target" = mpg), "TrainRandomForestAuto")
}

set.seed(42)

generateTrainRandomForestFormulaAuto()
generateTrainRandomForestAuto()

iris = loadIrisCsv("Iris")

iris_x = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

predictRandomForestIris = function(iris.randomForest, data, targetName){
	species = predict(iris.randomForest, newdata = data)
	probabilities = predict(iris.randomForest, newdata = data, type = "prob")

	result = data.frame(species)
	names(result) = c(targetName)
	result = data.frame(result, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE)

	return (result)
}

generateRandomForestFormulaIris = function(){
	iris.randomForest = randomForest(Species ~ ., data = iris, ntree = 7)
	iris.randomForest = decorate(iris.randomForest, compact = TRUE)
	print(iris.randomForest)

	storeRds(iris.randomForest, "RandomForestFormulaIris")
	storeCsv(predictRandomForestIris(iris.randomForest, iris, "Species"), "RandomForestFormulaIris")
}

generateRandomForestCustFormulaIris = function(){
	iris.randomForest = randomForest(Species ~ . - Sepal.Length + I(Sepal.Length / Sepal.Width) - Petal.Length + I(Petal.Length / Petal.Width), data = iris, ntree = 7)
	print(iris.randomForest)

	storeRds(iris.randomForest, "RandomForestCustFormulaIris")
	storeCsv(predictRandomForestIris(iris.randomForest, iris, "Species"), "RandomForestCustFormulaIris")
}

generateRandomForestIris = function(){
	iris.randomForest = randomForest(x = iris_x, y = iris_y, ntree = 7)
	iris.randomForest = decorate(iris.randomForest, compact = TRUE)
	print(iris.randomForest)

	storeRds(iris.randomForest, "RandomForestIris")
	storeCsv(predictRandomForestIris(iris.randomForest, iris_x, "_target"), "RandomForestIris")
}

set.seed(42)

generateRandomForestFormulaIris()
generateRandomForestCustFormulaIris()
generateRandomForestIris()

generateTrainRandomForestIris = function(){
	iris.train = train(x = iris_x, y = iris_y, method = "rf", preProcess = c("range"), ntree = 7)
	print(iris.train)

	storeRds(iris.train, "TrainRandomForestIris")
	storeCsv(predictRandomForestIris(iris.train, iris_x, "_target"), "TrainRandomForestIris")
}

generateTrainRandomForestFormulaIris = function(){
	iris.train = train(Species ~ ., data = iris, method = "rf", preProcess = c("center", "scale"), ntree = 7)
	print(iris.train)

	storeRds(iris.train, "TrainRandomForestFormulaIris")
	storeCsv(predictRandomForestIris(iris.train, iris, "_target"), "TrainRandomForestFormulaIris")
}

set.seed(42)

generateTrainRandomForestFormulaIris()
generateTrainRandomForestIris()

wine_quality = loadWineQualityCsv("WineQuality")

wine_quality_x = wine_quality[, -ncol(wine_quality)]
wine_quality_y = wine_quality[, ncol(wine_quality)]

generateRandomForestFormulaWineQuality = function(){
	wine_quality.randomForest = randomForest(quality ~ ., data = wine_quality, ntree = 31)
	wine_quality.randomForest = decorate(wine_quality.randomForest, compact = TRUE)
	print(wine_quality.randomForest)

	quality = predict(wine_quality.randomForest, newdata = wine_quality)

	storeRds(wine_quality.randomForest, "RandomForestFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "RandomForestFormulaWineQuality")
}

generateRandomForestWineQuality = function(){
	wine_quality.randomForest = randomForest(x = wine_quality_x, y = wine_quality_y, ntree = 31)
	print(wine_quality.randomForest)

	quality = predict(wine_quality.randomForest, newdata = wine_quality_x)

	storeRds(wine_quality.randomForest, "RandomForestWineQuality")
	storeCsv(data.frame("_target" = quality), "RandomForestWineQuality")
}

set.seed(42)

generateRandomForestFormulaWineQuality()
generateRandomForestWineQuality()

wine_color = loadWineColorCsv("WineColor")

wine_color_x = wine_color[, -ncol(wine_color)]
wine_color_y = wine_color[, ncol(wine_color)]

predictRandomForestWineColor = function(wine_color.randomForest, data, targetName){
	color = predict(wine_color.randomForest, newdata = wine_color)
	probabilities = predict(wine_color.randomForest, newdata = wine_color, type = "prob")

	result = data.frame(color)
	names(result) = c(targetName)
	result = data.frame(result, "probability(red)" = probabilities[, 1], "probability(white)" = probabilities[, 2], check.names = FALSE)

	return (result)
}

generateRandomForestFormulaWineColor = function(){
	wine_color.randomForest = randomForest(color ~ ., data = wine_color, ntree = 31)
	wine_color.randomForest = decorate(wine_color.randomForest, compact = TRUE)
	print(wine_color.randomForest)

	storeRds(wine_color.randomForest, "RandomForestFormulaWineColor")
	storeCsv(predictRandomForestWineColor(wine_color.randomForest, wine_color, "color"), "RandomForestFormulaWineColor")
}

generateRandomForestWineColor = function(){
	wine_color.randomForest = randomForest(x = wine_color_x, y = wine_color_y, ntree = 31)
	print(wine_color.randomForest)

	storeRds(wine_color.randomForest, "RandomForestWineColor")
	storeCsv(predictRandomForestWineColor(wine_color.randomForest, wine_color_x, "_target"), "RandomForestWineColor")
}

set.seed(42)

generateRandomForestFormulaWineColor()
generateRandomForestWineColor()

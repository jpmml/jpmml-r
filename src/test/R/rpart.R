library("caret")
library("rpart")

source("util.R")

audit = loadAuditCsv("Audit")

generateRPartAudit = function(){
	audit.rpart = rpart(Adjusted ~ ., data = audit, control = list(cp = 0, usesurrogate = 0))
	print(audit.rpart)

	adjusted = predict(audit.rpart, newdata = audit, type = "class")
	probabilities = predict(audit.rpart, newdata = audit, type = "prob")

	storeRds(audit.rpart, "RPartAudit")
	storeCsv(data.frame("Adjusted" = adjusted, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE), "RPartAudit")
}

set.seed(42)

generateRPartAudit()

auto = loadAutoCsv("Auto")

generateRPartAuto = function(){
	auto.rpart = rpart(mpg ~ ., data = auto, control = list(cp = 0, usesurrogate = 0))
	print(auto.rpart)

	mpg = predict(auto.rpart, newdata = auto)

	storeRds(auto.rpart, "RPartAuto")
	storeCsv(data.frame("mpg" = mpg), "RPartAuto")
}

set.seed(42)

generateRPartAuto()

iris = loadIrisCsv("Iris")

generateRPartIris = function(){
	iris.rpart = rpart(Species ~ ., data = iris, control = list(cp = 0, usesurrogate = 0))
	print(iris.rpart)

	species = predict(iris.rpart, newdata = iris, type = "class")
	probabilities = predict(iris.rpart, newdata = iris, type = "prob")

	storeRds(iris.rpart, "RPartIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "RPartIris")
}

set.seed(42)

generateRPartIris()

generateTrainRPartIris = function(){
	iris.train = train(Species ~ ., data = iris, method = "rpart")
	print(iris.train)

	species = predict(iris.train, newdata = iris)
	probabilities = predict(iris.train, newdata = iris, type = "prob")

	storeRds(iris.train, "TrainRPartIris")
	storeCsv(data.frame(".outcome" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "TrainRPartIris")
}

set.seed(42)

generateTrainRPartIris()

wine_quality = loadWineQualityCsv("WineQuality")

generateRPartWineQuality = function(){
	wine_quality.rpart = rpart(quality ~ ., data = wine_quality, control = list(cp = 0, usesurrogate = 0))
	print(wine_quality.rpart)

	quality = predict(wine_quality.rpart, newdata = wine_quality)

	storeRds(wine_quality.rpart, "RPartWineQuality")
	storeCsv(data.frame("quality" = quality), "RPartWineQuality")
}

set.seed(42)

generateRPartWineQuality()

wine_color = loadWineColorCsv("WineColor")

generateRPartWineColor = function(){
	wine_color.rpart = rpart(color ~ ., data = wine_color, control = list(cp = 0, usesurrogate = 0))
	print(wine_color.rpart)

	color = predict(wine_color.rpart, newdata = wine_color, type = "class")
	probabilities = predict(wine_color.rpart, newdata = wine_color, type = "prob")

	storeRds(wine_color.rpart, "RPartWineColor")
	storeCsv(data.frame("color" = color, "probability(red)" = probabilities[, 1], "probability(white)" = probabilities[, 2], check.names = FALSE), "RPartWineColor")
}

set.seed(42)

generateRPartWineColor()
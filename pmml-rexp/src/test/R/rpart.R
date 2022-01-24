library("caret")
library("dplyr")
library("recipes")
library("rpart")
library("r2pmml")

source("util.R")

audit = loadAuditCsv("Audit")

predictRPartAudit = function(audit.rpart, data){
	adjusted = predict(audit.rpart, newdata = data, type = "class")
	probabilities = predict(audit.rpart, newdata = data, type = "prob")

	result = data.frame("Adjusted" = adjusted, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE)

	return (result)
}

generateRPartAudit = function(){
	audit.rpart = rpart(Adjusted ~ ., data = audit, control = list(cp = 0, usesurrogate = 0))
	print(audit.rpart)

	storeRds(audit.rpart, "RPartAudit")
	storeCsv(predictRPartAudit(audit.rpart, audit), "RPartAudit")
}

set.seed(42)

generateRPartAudit()

audit = loadAuditCsv("AuditNA")

generateRPartAuditNA = function(){
	audit.rpart = rpart(Adjusted ~ ., data = audit, control = list(cp = 0, maxsurrogate = 2, usesurrogate = 1))
	print(audit.rpart)

	storeRds(audit.rpart, "RPartAuditNA")
	storeCsv(predictRPartAudit(audit.rpart, audit), "RPartAuditNA")
}

set.seed(42)

generateRPartAuditNA()

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

auto = loadAutoCsv("AutoNA")

generateRPartAutoNA = function(){
	auto.rpart = rpart(mpg ~ ., data = auto, control = list(cp = 0, maxsurrogate = 1, usesurrogate = 1))
	print(auto.rpart)

	mpg = predict(auto.rpart, newdata = auto)

	storeRds(auto.rpart, "RPartAutoNA")
	storeCsv(data.frame("mpg" = mpg), "RPartAutoNA")
}

set.seed(42)

generateRPartAutoNA()

iris = loadIrisCsv("Iris")

iris.recipe = recipe(Species ~ ., data = iris)

predictRPartIris = function(iris.rpart, data){
	species = predict(iris.rpart, newdata = data, type = "class")
	probabilities = predict(iris.rpart, newdata = data, type = "prob")

	result = data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE)

	return (result)
}

generateRPartIris = function(){
	iris.rpart = rpart(Species ~ ., data = iris, control = list(cp = 0, usesurrogate = 0))
	print(iris.rpart)

	storeRds(iris.rpart, "RPartIris")
	storeCsv(predictRPartIris(iris.rpart, iris), "RPartIris")
}

set.seed(42)

generateRPartIris()

generateTrainRPartIris = function(){
	iris.train = train(iris.recipe, data = iris, method = "rpart", control = list(c = 0, usesurrogate = 0))
	iris.train = verify(iris.train, newdata = sample_n(iris, 10))
	print(iris.train)

	species = predict(iris.train, newdata = iris)
	probabilities = predict(iris.train, newdata = iris, type = "prob")

	storeRds(iris.train, "TrainRPartIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "TrainRPartIris")
}

set.seed(42)

generateTrainRPartIris()

iris = loadIrisCsv("IrisNA")

generateRPartIrisNA = function(){
	iris.rpart = rpart(Species ~ ., data = iris, control = list(cp = 0, maxsurrogate = 1, usesurrogate = 1))
	print(iris.rpart)

	storeRds(iris.rpart, "RPartIrisNA")
	storeCsv(predictRPartIris(iris.rpart, iris), "RPartIrisNA")
}

set.seed(42)

generateRPartIrisNA()

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

wine_quality = loadWineQualityCsv("WineQualityNA")

generateRPartWineQualityNA = function(){
	wine_quality.rpart = rpart(quality ~ ., data = wine_quality, control = list(cp = 0, maxsurrogate = 1, usesurrogate = 2))
	print(wine_quality.rpart)

	quality = predict(wine_quality.rpart, newdata = wine_quality)

	storeRds(wine_quality.rpart, "RPartWineQualityNA")
	storeCsv(data.frame("quality" = quality), "RPartWineQualityNA")
}

set.seed(42)

generateRPartWineQualityNA()

wine_color = loadWineColorCsv("WineColor")

predictRPartWineColor = function(wine_color.rpart, data){
	color = predict(wine_color.rpart, newdata = data, type = "class")
	probabilities = predict(wine_color.rpart, newdata = data, type = "prob")

	result = data.frame("color" = color, "probability(red)" = probabilities[, 1], "probability(white)" = probabilities[, 2], check.names = FALSE)

	return (result)
}

generateRPartWineColor = function(){
	wine_color.rpart = rpart(color ~ ., data = wine_color, control = list(cp = 0, usesurrogate = 0))
	print(wine_color.rpart)

	storeRds(wine_color.rpart, "RPartWineColor")
	storeCsv(predictRPartWineColor(wine_color.rpart, wine_color), "RPartWineColor")
}

set.seed(42)

generateRPartWineColor()

wine_color = loadWineColorCsv("WineColorNA")

generateRPartWineColorNA = function(){
	wine_color.rpart = rpart(color ~ ., data = wine_color, control = list(cp = 0, maxsurrogate = 1, usesurrogate = 2))
	print(wine_color.rpart)

	storeRds(wine_color.rpart, "RPartWineColorNA")
	storeCsv(predictRPartWineColor(wine_color.rpart, wine_color), "RPartWineColorNA")
}

set.seed(42)

generateRPartWineColorNA()
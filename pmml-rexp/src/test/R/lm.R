library("mlr")
library("plyr")

source("util.R")

auto = loadAutoCsv("Auto")

generateLMFormulaAuto = function(){
	auto.lm = lm(mpg ~ ., data = auto)
	print(auto.lm)

	mpg = predict(auto.lm, newdata = auto)

	storeRds(auto.lm, "LMFormulaAuto", ascii = TRUE)
	storeCsv(data.frame("mpg" = mpg), "LMFormulaAuto")
}

generateLMCustFormulaAuto = function(){
	auto.lm = lm(mpg ~ I(displacement / cylinders) + (. - horsepower - weight - origin) ^ 2 + base::cut(horsepower, breaks = c(20, 40, 60, 80, 100, 150, 200, 400)) + I(log(weight)) + plyr::mapvalues(origin, from = c(1, 2, 3), to = c(-1.0, -2.0, -3.0)), data = auto)
	print(auto.lm)

	mpg = predict(auto.lm, newdata = auto)

	storeRds(auto.lm, "LMCustFormulaAuto", ascii = TRUE)
	storeCsv(data.frame("mpg" = mpg), "LMCustFormulaAuto")
}

generateLMFormulaAuto()
generateLMCustFormulaAuto()

generateWrappedLMFormulaAuto = function(){
	auto.task = makeRegrTask(data = auto, target = "mpg")
	regr.lm = makeLearner("regr.lm")

	auto.mlr = train(regr.lm, auto.task)
	print(auto.mlr)

	mpg = as.data.frame(predict(auto.mlr, newdata = auto))

	storeRds(auto.mlr, "WrappedLMFormulaAuto")
	storeCsv(data.frame("mpg" = mpg$response), "WrappedLMFormulaAuto")
}

generateWrappedLMFormulaAuto()

wine_quality = loadWineQualityCsv("WineQuality")

generateLMFormulaWineQuality = function(){
	wine_quality.lm = lm(quality ~ ., data = wine_quality)
	print(wine_quality.lm)

	quality = predict(wine_quality.lm, newdata = wine_quality)

	storeRds(wine_quality.lm, "LMFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "LMFormulaWineQuality")
}

generateLMFormulaWineQuality()

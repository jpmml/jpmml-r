library("elmNN")
library("r2pmml")

source("util.R")

auto = loadAutoCsv("Auto")

generateElmNNFormulaAuto = function(){
	auto.elmNN = elmtrain(mpg ~ ., data = auto, nhid = 11, actfun = "purelin")
	auto.elmNN = decorate(auto.elmNN, dataset = auto)
	print(auto.elmNN)

	mpg = predict(auto.elmNN, newdata = auto)

	storeRds(auto.elmNN, "ElmNNFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "ElmNNFormulaAuto")
}

set.seed(42)

generateElmNNFormulaAuto()

wine_quality = loadWineQualityCsv("WineQuality")

generateElmNNFormulaWineQuality = function(){
	wine_quality.elmNN = elmtrain(quality ~ ., data = wine_quality, nhid = 9, actfun = "purelin")
	wine_quality.elmNN = decorate(wine_quality.elmNN, dataset = wine_quality)
	print(wine_quality.elmNN)

	quality = predict(wine_quality.elmNN, newdata = wine_quality)

	storeRds(wine_quality.elmNN, "ElmNNFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "ElmNNFormulaWineQuality")
}

set.seed(42)

generateElmNNFormulaWineQuality()
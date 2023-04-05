library("elmNNRcpp")
library("r2pmml")

source("util.R")

auto = loadAutoCsv("Auto")

generateElmFormulaAuto = function(){
	auto.elm = elm(mpg ~ ., data = auto, nhid = 11, actfun = "purelin", bias = TRUE)
	auto.elm = decorate(auto.elm, data = auto)
	print(auto.elm)

	mpg = predict(auto.elm, newdata = auto)

	storeRds(auto.elm, "ElmFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "ElmFormulaAuto")
}

set.seed(42)

generateElmFormulaAuto()

wine_quality = loadWineQualityCsv("WineQuality")

generateElmFormulaWineQuality = function(){
	wine_quality.elm = elm(quality ~ ., data = wine_quality, nhid = 9, actfun = "purelin")
	wine_quality.elm = decorate(wine_quality.elm, data = wine_quality)
	print(wine_quality.elm)

	quality = predict(wine_quality.elm, newdata = wine_quality)

	storeRds(wine_quality.elm, "ElmFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "ElmFormulaWineQuality")
}

set.seed(42)

generateElmFormulaWineQuality()

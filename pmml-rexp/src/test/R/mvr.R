library("caret")
library("pls")
library("r2pmml")

source("util.R")

auto = loadAutoCsv("Auto")

generatePLSRegressionFormulaAuto = function(){
	auto.mvr = plsr(mpg ~ ., data = auto, scale = TRUE, ncomp = 3)
	print(auto.mvr)

	mpg = predict(auto.mvr, newdata = auto, ncomp = 3)

	storeRds(auto.mvr, "PLSRegressionFormulaAuto")
	storeCsv(data.frame("mpg" = mpg[, 1, 1]), "PLSRegressionFormulaAuto")
}

generatePLSRegressionCustFormulaAuto = function(){
	auto.mvr = plsr(mpg ~ . - horsepower - weight + weight:horsepower + weight:acceleration + I(displacement / cylinders) + base::cut(horsepower, breaks = c(20, 40, 60, 80, 100, 150, 200, 400)) + I(log(weight)), data = auto, ncomp = 3)
	print(auto.mvr)

	mpg = predict(auto.mvr, newdata = auto, ncomp = 3)

	storeRds(auto.mvr, "PLSRegressionCustFormulaAuto")
	storeCsv(data.frame("mpg" = mpg[, 1, 1]), "PLSRegressionCustFormulaAuto")
}

generatePLSRegressionFormulaAuto()
generatePLSRegressionCustFormulaAuto()

wine_quality.raw = loadWineQualityCsv("WineQuality")
wine_quality.raw$quality = as.factor(wine_quality.raw$quality)

wine_quality.preProc = preProcess(wine_quality.raw, method = c("center"))

wine_quality = predict(wine_quality.preProc, wine_quality.raw)
wine_quality$quality = as.integer(wine_quality$quality)

generatePLSRegressionFormulaWineQuality = function(){
	wine_quality.mvr = mvr(quality ~ ., data = wine_quality, scale = TRUE, ncomp = 3)
	wine_quality.mvr = decorate(wine_quality.mvr, preProcess = wine_quality.preProc)
	print(wine_quality.mvr)

	quality = predict(wine_quality.mvr, newdata = wine_quality, ncomp = 3)

	storeRds(wine_quality.mvr, "PLSRegressionFormulaWineQuality")
	storeCsv(data.frame("quality" = quality[, 1, 1]), "PLSRegressionFormulaWineQuality")
}

generatePLSRegressionFormulaWineQuality()
library("plyr")

source("util.R")

auto = loadAutoCsv("Auto")

generateLinearRegressionFormulaAuto = function(){
	auto.lm = lm(mpg ~ ., data = auto)
	print(auto.lm)

	mpg = predict(auto.lm, newdata = auto)

	storeRds(auto.lm, "LinearRegressionFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "LinearRegressionFormulaAuto")
}

generateLinearRegressionCustFormulaAuto = function(){
	auto.lm = lm(mpg ~ I(displacement / cylinders) + (. - horsepower - weight - origin) ^ 2 + cut(horsepower, breaks = c(20, 40, 60, 80, 100, 150, 200, 400)) + I(log(weight)) + mapvalues(origin, from = c(1, 2, 3), to = c(-1.0, -2.0, -3.0)), data = auto)
	print(auto.lm)

	mpg = predict(auto.lm, newdata = auto)

	storeRds(auto.lm, "LinearRegressionCustFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "LinearRegressionCustFormulaAuto")
}

generateLinearRegressionFormulaAuto()
generateLinearRegressionCustFormulaAuto()

wine_quality = loadWineQualityCsv("WineQuality")

generateLinearRegressionFormulaWineQuality = function(){
	wine_quality.lm = lm(quality ~ ., data = wine_quality)
	print(wine_quality.lm)

	quality = predict(wine_quality.lm, newdata = wine_quality)

	storeRds(wine_quality.lm, "LinearRegressionFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "LinearRegressionFormulaWineQuality")
}

generateLinearRegressionFormulaWineQuality()
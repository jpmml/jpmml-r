library("rms")

source("util.R")

auto = loadAutoCsv("Auto")

generateOLSRegressionFormulaAuto = function(){
	auto.ols = ols(mpg ~ cylinders + displacement + horsepower + weight + acceleration + model_year + origin, data = auto)
	print(auto.ols)

	mpg = predict(auto.ols, newdata = auto)

	storeRds(auto.ols, "OLSRegressionFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "OLSRegressionFormulaAuto")
}

generateOLSRegressionFormulaAuto()

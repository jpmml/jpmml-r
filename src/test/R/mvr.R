library("pls")

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

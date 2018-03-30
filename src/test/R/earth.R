library("earth")
library("r2pmml")

source("util.R")

auto = loadAutoCsv("Auto")

generateEarthFormulaAuto = function(){
	auto.earth = earth(mpg ~ ., data = auto, degree = 2)
	auto.earth = decorate(auto.earth, data = auto)
	print(auto.earth)

	mpg = predict(auto.earth, newdata = auto)

	storeRds(auto.earth, "EarthFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "EarthFormulaAuto")
}

generateEarthCustFormulaAuto = function(){
	auto.earth = earth(mpg ~ . + weight:horsepower + weight:acceleration + I(displacement / cylinders) + I(log(weight)), data = auto, degree = 3)
	auto.earth = decorate(auto.earth, data = auto)
	print(auto.earth)

	mpg = predict(auto.earth)

	storeRds(auto.earth, "EarthCustFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "EarthCustFormulaAuto")
}

generateEarthFormulaAuto()
generateEarthCustFormulaAuto()

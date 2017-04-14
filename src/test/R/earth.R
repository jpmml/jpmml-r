library("earth")

source("util.R")

auto = loadAutoCsv("Auto")

generateEarthFormulaAuto = function(){
	auto.earth = earth(mpg ~ ., data = auto, degree = 2)
	auto.earth$xlevels = stats:::.getXlevels(auto.earth$terms, auto)
	print(auto.earth)

	mpg = predict(auto.earth, newdata = auto)

	storeRds(auto.earth, "EarthFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "EarthFormulaAuto")
}

generateEarthCustFormulaAuto = function(){
	auto.earth = earth(mpg ~ . + weight:horsepower + weight:acceleration + I(displacement / cylinders) + I(log(weight)), data = auto, degree = 3)
	auto.earth$xlevels = list("origin" = c("1", "2", "3"))
	print(auto.earth)

	mpg = predict(auto.earth)

	storeRds(auto.earth, "EarthCustFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "EarthCustFormulaAuto")
}

generateEarthFormulaAuto()
generateEarthCustFormulaAuto()

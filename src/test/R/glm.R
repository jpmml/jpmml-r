library("plyr")

source("util.R")

audit = loadAuditCsv("Audit")

predictGeneralRegressionAudit = function(audit.glm, data){
	probabilities = predict(audit.glm, newdata = audit, type = "response")

	result = data.frame("Adjusted" = as.integer(probabilities > 0.5), "probability_0" = (1 - probabilities), "probability_1" = probabilities)

	return (result)
}

generateGeneralRegressionFormulaAudit = function(){
	audit.glm = glm(Adjusted ~ ., data = audit, family = binomial)
	print(audit.glm)

	storeRds(audit.glm, "GeneralRegressionFormulaAudit")
	storeCsv(predictGeneralRegressionAudit(audit.glm, audit), "GeneralRegressionFormulaAudit")
}

generateGeneralRegressionCustFormulaAudit = function(){
	audit.glm = glm(Adjusted ~ . - Education + plyr::revalue(Education, c(Yr1t4 = "Yr1t6", Yr5t6 = "Yr1t6", Yr7t8 = "Yr7t9", Yr9 = "Yr7t9", Yr10 = "Yr10t12", Yr11 = "Yr10t12", Yr12 = "Yr10t12")) - Income + base::cut(Income, breaks = c(100, 1000, 10000, 100000, 1000000)) + Gender:Age + Gender:Marital, data = audit, family = binomial)
	print(audit.glm)

	storeRds(audit.glm, "GeneralRegressionCustFormulaAudit")
	storeCsv(predictGeneralRegressionAudit(audit.glm, audit), "GeneralRegressionCustFormulaAudit")
}

generateGeneralRegressionFormulaAudit()
generateGeneralRegressionCustFormulaAudit()

auto = loadAutoCsv("Auto")

generateGeneralRegressionFormulaAuto = function(){
	auto.glm = glm(mpg ~ ., data = auto)
	print(auto.glm)

	mpg = predict(auto.glm, newdata = auto)

	storeRds(auto.glm, "GeneralRegressionFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "GeneralRegressionFormulaAuto")
}

generateGeneralRegressionCustFormulaAuto = function(){
	auto.glm = glm(mpg ~ (. - horsepower - weight - origin) ^ 2 + base::cut(horsepower, breaks = 10, dig.lab = 4) + I(log(weight)) + plyr::revalue(origin, replace = c("1" = "US", "2" = "Non-US", "3" = "Non-US")), data = auto)
	print(auto.glm)

	mpg = predict(auto.glm, newdata = auto)

	storeRds(auto.glm, "GeneralRegressionCustFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "GeneralRegressionCustFormulaAuto")
}

generateGeneralRegressionFormulaAuto()
generateGeneralRegressionCustFormulaAuto()

visit = loadVisitCsv("Visit")

generateGeneralRegressionFormulaVisit = function(){
	visit.glm = glm(docvis ~ ., data = visit, family = poisson(link = "sqrt"))
	print(visit.glm)

	docvis = predict(visit.glm, newdata = visit, type = "response")

	storeRds(visit.glm, "GeneralRegressionFormulaVisit")
	storeCsv(data.frame("docvis" = docvis), "GeneralRegressionFormulaVisit")
}

generateGeneralRegressionFormulaVisit()

wine_quality = loadWineQualityCsv("WineQuality")

generateGeneralRegressionFormulaWineQuality = function(){
	wine_quality.glm = glm(quality ~ ., data = wine_quality)
	print(wine_quality.glm)

	quality = predict(wine_quality.glm, newdata = wine_quality)

	storeRds(wine_quality.glm, "GeneralRegressionFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "GeneralRegressionFormulaWineQuality")
}

generateGeneralRegressionCustFormulaWineQuality = function(){
	wine_quality.glm = glm(quality ~ base::cut(fixed.acidity, breaks = 10, dig.lab = 6) + base::cut(volatile.acidity, breaks = c(0, 0.5, 1.0, 1.5, 2.0)) + I(citric.acid) + I(residual.sugar) + I(chlorides) + base::cut(free.sulfur.dioxide / total.sulfur.dioxide, "breaks" = c(0, 0.2, 0.4, 0.6, 0.8, 1.0)) + + I(density) + I(pH) + I(sulphates) + I(alcohol), data = wine_quality)
	print(wine_quality.glm)

	quality = predict(wine_quality.glm, newdata = wine_quality)

	storeRds(wine_quality.glm, "GeneralRegressionCustFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "GeneralRegressionCustFormulaWineQuality")
}

generateGeneralRegressionFormulaWineQuality()
generateGeneralRegressionCustFormulaWineQuality()

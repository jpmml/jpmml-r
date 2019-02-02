library("caret")
library("dplyr")
library("plyr")
library("recipes")
library("r2pmml")

source("util.R")

audit = loadAuditCsv("Audit")

audit.recipe = recipe(Adjusted ~ ., data = audit)

predictGLMAudit = function(audit.glm){
	probabilities = predict(audit.glm, newdata = audit, type = "response")

	result = data.frame("Adjusted" = as.integer(probabilities > 0.5), "probability(0)" = (1 - probabilities), "probability(1)" = probabilities, check.names = FALSE)

	return (result)
}

generateGLMFormulaAudit = function(){
	audit.glm = glm(Adjusted ~ ., data = audit, family = binomial)
	print(audit.glm)

	storeRds(audit.glm, "GLMFormulaAudit")
	storeCsv(predictGLMAudit(audit.glm), "GLMFormulaAudit")
}

generateGLMCustFormulaAudit = function(){
	audit.glm = glm(Adjusted ~ . - Education + plyr::revalue(Education, c(Yr1t4 = "Yr1t6", Yr5t6 = "Yr1t6", Yr7t8 = "Yr7t9", Yr9 = "Yr7t9", Yr10 = "Yr10t12", Yr11 = "Yr10t12", Yr12 = "Yr10t12")) - Income + base::cut(Income, breaks = c(100, 1000, 10000, 100000, 1000000)) + Gender:Age + Gender:Marital + I(Hours < 30) + (Age < 18 | Age > 65) + ((Income / (Hours * 52)) < 8), data = audit, family = binomial)
	print(audit.glm)

	storeRds(audit.glm, "GLMCustFormulaAudit")
	storeCsv(predictGLMAudit(audit.glm), "GLMCustFormulaAudit")
}

generateGLMFormulaAudit()
generateGLMCustFormulaAudit()

generateTrainGLMFormulaAudit = function(){
	audit.train = train(audit.recipe, data = audit, method = "glm")
	audit.train = verify(audit.train, newdata = sample_n(audit, 100))
	print(audit.train)

	adjusted = predict(audit.train, newdata = audit)
	probabilities = predict(audit.train, newdata = audit, type = "prob")

	storeRds(audit.train, "TrainGLMFormulaAudit")
	storeCsv(data.frame("Adjusted" = adjusted, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE), "TrainGLMFormulaAudit")
}

generateTrainGLMFormulaAudit()

auto = loadAutoCsv("Auto")

auto.recipe = recipe(mpg ~ ., data = auto)

generateGLMFormulaAuto = function(){
	auto.glm = glm(mpg ~ ., data = auto)
	print(auto.glm)

	mpg = predict(auto.glm, newdata = auto)

	storeRds(auto.glm, "GLMFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "GLMFormulaAuto")
}

generateGLMCustFormulaAuto = function(){
	auto.glm = glm(mpg ~ (. - horsepower - weight - origin) ^ 2 + base::cut(horsepower, breaks = 10, dig.lab = 4) + I(log(weight)) + I(weight ^ 2) + I(weight ^ 3) + plyr::revalue(origin, replace = c("1" = "US", "2" = "Non-US", "3" = "Non-US")), data = auto)
	print(auto.glm)

	mpg = predict(auto.glm, newdata = auto)

	storeRds(auto.glm, "GLMCustFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "GLMCustFormulaAuto")
}

generateGLMFormulaAuto()
generateGLMCustFormulaAuto()

generateTrainGLMFormulaAuto = function(){
	auto.train = train(auto.recipe, data = auto, method = "glm")
	auto.train = verify(auto.train, newdata = sample_n(auto, 50))
	print(auto.train)

	mpg = predict(auto.train, newdata = auto)

	storeRds(auto.train, "TrainGLMFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "TrainGLMFormulaAuto")
}

generateTrainGLMFormulaAuto()

visit = loadVisitCsv("Visit")

generateGLMFormulaVisit = function(){
	visit.glm = glm(docvis ~ ., data = visit, family = poisson(link = "sqrt"))
	print(visit.glm)

	docvis = predict(visit.glm, newdata = visit, type = "response")

	storeRds(visit.glm, "GLMFormulaVisit")
	storeCsv(data.frame("docvis" = docvis), "GLMFormulaVisit")
}

generateGLMFormulaVisit()

wine_quality = loadWineQualityCsv("WineQuality")

generateGLMFormulaWineQuality = function(){
	wine_quality.glm = glm(quality ~ ., data = wine_quality)
	print(wine_quality.glm)

	quality = predict(wine_quality.glm, newdata = wine_quality)

	storeRds(wine_quality.glm, "GLMFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "GLMFormulaWineQuality")
}

generateGLMCustFormulaWineQuality = function(){
	wine_quality.glm = glm(quality ~ base::cut(fixed.acidity, breaks = 10, dig.lab = 6) + base::cut(volatile.acidity, breaks = c(0, 0.5, 1.0, 1.5, 2.0)) + I(citric.acid) + I(residual.sugar) + I(chlorides) + base::cut(free.sulfur.dioxide / total.sulfur.dioxide, "breaks" = c(0, 0.2, 0.4, 0.6, 0.8, 1.0)) + + I(density) + I(pH) + I(sulphates) + I(alcohol), data = wine_quality)
	print(wine_quality.glm)

	quality = predict(wine_quality.glm, newdata = wine_quality)

	storeRds(wine_quality.glm, "GLMCustFormulaWineQuality")
	storeCsv(data.frame("quality" = quality), "GLMCustFormulaWineQuality")
}

generateGLMFormulaWineQuality()
generateGLMCustFormulaWineQuality()

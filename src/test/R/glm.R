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
	audit.glm = glm(Adjusted ~ . + Gender:Age + Gender:Marital, data = audit, family = binomial)
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
	auto.glm = glm(mpg ~ (.) ^ 2 + I(log(weight)), data = auto)
	print(auto.glm)

	mpg = predict(auto.glm, newdata = auto)

	storeRds(auto.glm, "GeneralRegressionCustFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "GeneralRegressionCustFormulaAuto")
}

generateGeneralRegressionFormulaAuto()
generateGeneralRegressionCustFormulaAuto()
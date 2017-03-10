library("rms")

source("util.R")

audit = loadAuditCsv("Audit")
levels(audit$Adjusted) = c("nope", "yep")

predictLogisticRegressionAudit = function(audit.lrm){
	probabilities = predict(audit.lrm, newdata = audit, type = "fitted")

	result = data.frame("Adjusted" = as.integer(probabilities > 0.5), "probability_0" = (1 - probabilities), "probability_1" = probabilities)

	return (result)
}

generateLogisticRegressionFormulaAudit = function(){
	audit.lrm = lrm(Adjusted ~ ., data = audit)
	print(audit.lrm)

	storeRds(audit.lrm, "LogisticRegressionFormulaAudit")
	storeCsv(predictLogisticRegressionAudit(audit.lrm), "LogisticRegressionFormulaAudit")
}

generateLogisticRegressionFormulaAudit()

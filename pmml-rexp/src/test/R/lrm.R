library("rms")

source("util.R")

audit = loadAuditCsv("Audit")

predictLRMAudit = function(audit.lrm){
	probabilities = predict(audit.lrm, newdata = audit, type = "fitted")

	result = data.frame("Adjusted" = as.integer(probabilities > 0.5), "probability(0)" = (1 - probabilities), "probability(1)" = probabilities, check.names = FALSE)

	return (result)
}

generateLRMFormulaAudit = function(){
	audit.lrm = lrm(Adjusted ~ ifelse(Age < 18, 18, ifelse(Age > 75, 75, Age)) + Employment + Education + Marital + Occupation + ifelse(Income > 250000, yes = 250000, no = Income) + Gender + Deductions + ifelse(Hours <= 80, Hours, 80), data = audit)
	print(audit.lrm)

	storeRds(audit.lrm, "LRMFormulaAudit")
	storeCsv(predictLRMAudit(audit.lrm), "LRMFormulaAudit")
}

generateLRMFormulaAudit()

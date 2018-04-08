library("ada")

source("util.R")

audit = loadAuditCsv("Audit")

audit_x = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]

predictAdaAudit = function(audit.ada, data, targetName){
	adjusted = predict(audit.ada, newdata = data)
	probabilities = predict(audit.ada, newdata = data, type = "prob")

	result = data.frame(adjusted)
	names(result) = c(targetName)
	result = data.frame(result, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE)

	return (result)
}

generateAdaFormulaAudit = function(){
	audit.ada = ada(Adjusted ~ ., data = audit, control = list(cp = 0, maxsurrogate = 0, usesurrogate = 0))
	print(audit.ada)

	storeRds(audit.ada, "AdaFormulaAudit")
	storeCsv(predictAdaAudit(audit.ada, audit, "Adjusted"), "AdaFormulaAudit")
}

generateAdaAudit = function(){
	audit.ada = ada(x = audit_x, y = audit_y, control = list(cp = 0, maxsurrogate = 0, usesurrogate = 0))
	print(audit.ada)

	storeRds(audit.ada, "AdaAudit")
	storeCsv(predictAdaAudit(audit.ada, audit_x, "_target"), "AdaAudit")
}

set.seed(42)

generateAdaFormulaAudit()
generateAdaAudit()

versicolor = loadVersicolorCsv("Versicolor")

generateAdaFormulaVersicolor = function(){
	versicolor.ada = ada(Species ~ ., data = versicolor, iter = 7, control = list(cp = 0, maxsurrogate = 0, usesurrogate = 0))
	print(versicolor.ada)

	species = predict(versicolor.ada, newdata = versicolor)
	probabilities = predict(versicolor.ada, newdata = versicolor, type = "prob")

	storeRds(versicolor.ada, "AdaFormulaVersicolor")
	storeCsv(data.frame("Species" = species, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE), "AdaFormulaVersicolor")
}

set.seed(42)

generateAdaFormulaVersicolor()

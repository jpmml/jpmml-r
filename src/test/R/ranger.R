library("caret")
library("ranger")

source("util.R")

getVariableLevels = function(data){
	return (sapply(data, levels))
}

audit = loadAuditCsv("Audit")

generateRangerAudit = function(){
	audit.ranger = ranger(Adjusted ~ ., data = audit, num.trees = 7, write.forest = TRUE)
	audit.ranger$variable.levels = getVariableLevels(audit)
	print(audit.ranger)

	Adjusted = predict(audit.ranger, data = audit)$predictions

	storeRds(audit.ranger, "RangerAudit")
	storeCsv(data.frame("_target" = Adjusted), "RangerAudit")
}

set.seed(42)

generateRangerAudit()

auto.raw = loadAutoCsv("AutoNA")

auto.preProc = preProcess(auto.raw, method = c("medianImpute"))

auto = predict(auto.preProc, auto.raw)

generateRangerAutoNA = function(){
	auto.ranger = ranger(mpg ~ ., data = auto, num.trees = 7, write.forest = TRUE)
	auto.ranger$variable.levels = getVariableLevels(auto)
	auto.ranger$preProcess = auto.preProc
	print(auto.ranger)

	mpg = predict(auto.ranger, data = auto)$prediction

	storeRds(auto.ranger, "RangerAutoNA")
	storeCsv(data.frame("_target" = mpg), "RangerAutoNA")
}

set.seed(42)

generateRangerAutoNA()

iris = loadIrisCsv("Iris")

generateRangerIris = function(){
	iris.ranger = ranger(Species ~ ., data = iris, num.trees = 7, write.forest = TRUE)
	iris.ranger$variable.levels = getVariableLevels(iris)
	print(iris.ranger)

	Species = predict(iris.ranger, data = iris)$predictions

	storeRds(iris.ranger, "RangerIris")
	storeCsv(data.frame("_target" = Species), "RangerIris")
}

set.seed(42)

generateRangerIris()
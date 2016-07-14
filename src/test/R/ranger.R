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

auto = loadAutoCsv("Auto")

generateRangerAuto = function(){
	auto.ranger = ranger(mpg ~ ., data = auto, num.trees = 7, write.forest = TRUE)
	auto.ranger$variable.levels = getVariableLevels(auto)
	print(auto.ranger)

	mpg = predict(auto.ranger, data = auto)$prediction

	storeRds(auto.ranger, "RangerAuto")
	storeCsv(data.frame("_target" = mpg), "RangerAuto")
}

set.seed(42)

generateRangerAuto()

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
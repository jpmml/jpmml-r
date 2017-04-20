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

	adjusted = predict(audit.ranger, data = audit)$predictions

	storeRds(audit.ranger, "RangerAudit")
	storeCsv(data.frame("_target" = adjusted), "RangerAudit")
}

generateRangerProbAudit = function(){
	audit.ranger = ranger(Adjusted ~ ., data = audit, probability = TRUE, num.trees = 7, write.forest = TRUE)
	audit.ranger$variable.levels = getVariableLevels(audit)
	print(audit.ranger)

	probabilities = predict(audit.ranger, data = audit)$predictions
	adjusted = apply(probabilities, 1, function(x) { colnames(probabilities)[which.max(x)] })

	storeRds(audit.ranger, "RangerProbAudit")
	storeCsv(data.frame("_target" = adjusted, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE), "RangerProbAudit")
}

set.seed(42)

generateRangerAudit()
generateRangerProbAudit()

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

	species = predict(iris.ranger, data = iris)$predictions

	storeRds(iris.ranger, "RangerIris")
	storeCsv(data.frame("_target" = species), "RangerIris")
}

generateRangerProbIris = function(){
	iris.ranger = ranger(Species ~ ., data = iris, probability = TRUE, num.trees = 7, write.forest = TRUE)
	iris.ranger$variable.levels = getVariableLevels(iris)
	print(iris.ranger)

	probabilities = predict(iris.ranger, data = iris)$predictions
	species = apply(probabilities, 1, function(x) { colnames(probabilities)[which.max(x)] })

	storeRds(iris.ranger, "RangerProbIris")
	storeCsv(data.frame("_target" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "RangerProbIris")
}

set.seed(42)

generateRangerIris()
generateRangerProbIris()

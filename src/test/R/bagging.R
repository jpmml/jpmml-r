library("adabag")

source("util.R")

audit = loadAuditCsv("Audit")

generateBaggingAudit = function(){
	audit.bagging = bagging(Adjusted ~ ., data = audit, mfinal = 100, control = list(cp = 0.01, maxsurrogate = 0, usesurrogate = 0))
	print(audit.bagging)

	prediction = predict(audit.bagging, newdata = audit)

	storeRds(audit.bagging, "BaggingAudit")
	storeCsv(data.frame("Adjusted" = prediction$class, "probability(0)" = prediction$prob[, 1], "probability(1)" = prediction$prob[, 2], check.names = FALSE), "BaggingAudit")
}

set.seed(42)

generateBaggingAudit()

iris = loadIrisCsv("Iris")

generateBaggingIris = function(){
	iris.bagging = bagging(Species ~ ., data = iris, mfinal = 7, control = list(cp = 0.01, maxsurrogate = 0, usesurrogate = 0))
	print(iris.bagging)

	prediction = predict(iris.bagging, newdata = iris)

	storeRds(iris.bagging, "BaggingIris")
	storeCsv(data.frame("Species" = prediction$class, "probability(setosa)" = prediction$prob[, 1], "probability(versicolor)" = prediction$prob[, 2], "probability(virginica)" = prediction$prob[, 3], check.names = FALSE), "BaggingIris")
}

set.seed(42)

generateBaggingIris()
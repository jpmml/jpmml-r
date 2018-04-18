library("adabag")

source("util.R")

audit = loadAuditCsv("Audit")

generateBoostingAudit = function(){
	audit.boosting = boosting(Adjusted ~ ., data = audit, mfinal = 31, control = list(cp = 0.01, maxsurrogate = 0, usesurrogate = 0))
	print(audit.boosting)

	prediction = predict(audit.boosting, newdata = audit)

	storeRds(audit.boosting, "BoostingAudit")
	storeCsv(data.frame("Adjusted" = prediction$class, "probability(0)" = prediction$prob[, 1], "probability(1)" = prediction$prob[, 2], check.names = FALSE), "BoostingAudit")
}

set.seed(42)

generateBoostingAudit()

iris = loadIrisCsv("Iris")

generateBoostingIris = function(){
	iris.boosting = boosting(Species ~ ., data = iris, mfinal = 7, control = list(cp = 0.01, maxsurrogate = 0, usesurrogate = 0))
	print(iris.boosting)

	prediction = predict(iris.boosting, newdata = iris)

	storeRds(iris.boosting, "BoostingIris")
	storeCsv(data.frame("Species" = prediction$class, "probability(setosa)" = prediction$prob[, 1], "probability(versicolor)" = prediction$prob[, 2], "probability(virginica)" = prediction$prob[, 3], check.names = FALSE), "BoostingIris")
}

set.seed(42)

generateBoostingIris()
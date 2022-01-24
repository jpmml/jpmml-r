library("nnet")

source("util.R")

audit = loadAuditCsv("Audit")

generateMultinomAudit = function(){
	audit.multinom = multinom(Adjusted ~ ., data = audit)
	print(audit.multinom)

	adjusted = predict(audit.multinom, newdata = audit, type = "class")
	probabilities = predict(audit.multinom, newdata = audit, type = "prob")

	storeRds(audit.multinom, "MultinomAudit")
	storeCsv(data.frame("Adjusted" = adjusted, "probability(0)" = (1 - probabilities), "probability(1)" = probabilities, check.names = FALSE), "MultinomAudit")
}

set.seed(42)

generateMultinomAudit()

iris = loadIrisCsv("Iris")

generateMultinomIris = function(){
	iris.multinom = multinom(Species ~ ., data = iris)
	print(iris.multinom)

	species = predict(iris.multinom, newdata = iris, type = "class")
	probabilities = predict(iris.multinom, newdata = iris, type = "prob")

	storeRds(iris.multinom, "MultinomIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "MultinomIris")
}

set.seed(42)

generateMultinomIris()

library("e1071")

source("util.R")

audit = loadAuditCsv("Audit")
audit$Deductions = NULL

audit_x = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]

predictNaiveBayesAudit = function(audit.nb, data){
	adjusted = predict(audit.nb, newdata = data, threshold = 0, type = "class")
	probabilities = predict(audit.nb, newdata = data, threshold = 0, type = "raw")
	probabilities[is.na(probabilities)] = 0

	result = data.frame("_target" = adjusted, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE)

	return (result)
}

generateNaiveBayesFormulaAudit = function(){
	audit.nb = naiveBayes(Adjusted ~ ., data = audit)
	print(audit.nb)

	storeRds(audit.nb, "NaiveBayesFormulaAudit")
	storeCsv(predictNaiveBayesAudit(audit.nb, audit), "NaiveBayesFormulaAudit")
}

generateNaiveBayesAudit = function(){
	audit.nb = naiveBayes(x = audit_x, y = audit_y)
	print(audit.nb)

	storeRds(audit.nb, "NaiveBayesAudit")
	storeCsv(predictNaiveBayesAudit(audit.nb, audit_x), "NaiveBayesAudit")
}

set.seed(42)

generateNaiveBayesFormulaAudit()
generateNaiveBayesAudit()

iris = loadIrisCsv("Iris")

iris_x = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

predictNaiveBayesIris = function(iris.nb, data){
	species = predict(iris.nb, newdata = iris, threshold = 0, type = "class")
	probabilities = predict(iris.nb, newdata = iris, threshold = 0, type = "raw")

	result = data.frame("_target" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE)

	return (result)
}

generateNaiveBayesFormulaIris = function(){
	iris.nb = naiveBayes(Species ~ ., data = iris)
	print(iris.nb)

	storeRds(iris.nb, "NaiveBayesFormulaIris")
	storeCsv(predictNaiveBayesIris(iris.nb, iris), "NaiveBayesFormulaIris")
}

generateNaiveBayesIris = function(){
	iris.nb = naiveBayes(x = iris_x, y = iris_y)
	print(iris.nb)

	storeRds(iris.nb, "NaiveBayesIris")
	storeCsv(predictNaiveBayesIris(iris.nb, iris_x), "NaiveBayesIris")
}

set.seed(42)

generateNaiveBayesFormulaIris()
generateNaiveBayesIris()

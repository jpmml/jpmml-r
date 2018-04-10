library("nnet")

source("util.R")

audit = loadAuditCsv("Audit")

generateNNetFormulaAudit = function(){
	audit.nnet = nnet(Adjusted ~ ., data = audit, size = 17, decay = 1e-3, maxit = 1000)
	print(audit.nnet)

	adjusted = predict(audit.nnet, newdata = audit, type = "class")
	probabilities = predict(audit.nnet, newdata = audit, type = "raw")

	storeRds(audit.nnet, "NNetFormulaAudit")
	storeCsv(data.frame("Adjusted" = adjusted, "probability(0)" = (1 - probabilities[, 1]), "probability(1)" = probabilities[, 1], check.names = FALSE), "NNetFormulaAudit")
}

set.seed(42)

generateNNetFormulaAudit()

auto = loadAutoCsv("Auto")

generateNNetFormulaAuto = function(){
	auto.nnet = nnet(mpg ~ ., data = auto, size = 10, decay = 0.1, linout = TRUE)
	print(auto.nnet)

	mpg = predict(auto.nnet, newdata = auto)

	storeRds(auto.nnet, "NNetFormulaAuto")
	storeCsv(data.frame("mpg" = mpg), "NNetFormulaAuto")
}

set.seed(42)

generateNNetFormulaAuto()

iris = loadIrisCsv("Iris")

generateNNetFormulaIris = function(){
	iris.nnet = nnet(Species ~ ., data = iris, size = 5)
	print(iris.nnet)

	species = predict(iris.nnet, newdata = iris, type = "class")
	probabilities = predict(iris.nnet, newdata = iris, type = "raw")

	storeRds(iris.nnet, "NNetFormulaIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "NNetFormulaIris")
}

set.seed(42)

generateNNetFormulaIris()

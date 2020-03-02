library("caret")
library("dplyr")
library("gbm")
library("mlr")
library("r2pmml")

source("util.R")

audit = loadAuditCsv("AuditNA")

# Error in gbm.fit: Deductions is not of type numeric, ordered, or factor
audit$Deductions = NULL

audit_x = audit[, -ncol(audit)]
audit_y = audit[, ncol(audit)]
audit_y = as.numeric(audit_y == "1")

predictGBMAudit = function(audit.gbm){
	probability = predict(audit.gbm, newdata = audit_x, type = "response", n.trees = 100)

	result = data.frame("Adjusted" = as.integer(probability > 0.5), "probability(0)" = (1 - probability), "probability(1)" = probability, check.names = FALSE)

	return (result)
}

generateGBMAdaBoostAuditNA = function(){
	audit.gbm = gbm.fit(x = audit_x, y = audit_y, distribution = "adaboost", interaction.depth = 3, shrinkage = 0.1, n.trees = 100, response.name = "Adjusted")
	print(audit.gbm)

	storeRds(audit.gbm, "GBMAdaBoostAuditNA")
	storeCsv(predictGBMAudit(audit.gbm), "GBMAdaBoostAuditNA")
}

generateGBMBernoulliAuditNA = function(){
	audit.gbm = gbm.fit(x = audit_x, y = audit_y, distribution = "bernoulli", interaction.depth = 3, shrinkage = 0.1, n.trees = 100, response.name = "Adjusted")
	print(audit.gbm)

	storeRds(audit.gbm, "GBMBernoulliAuditNA")
	storeCsv(predictGBMAudit(audit.gbm), "GBMBernoulliAuditNA")
}

set.seed(42)

generateGBMAdaBoostAuditNA()
generateGBMBernoulliAuditNA()

generateWrappedGBMAdaBoostAudit = function(){
	audit.task = makeClassifTask(data = audit, target = "Adjusted")
	classif.gbm = makeLearner("classif.gbm", distribution = "adaboost", shrinkage = 0.1, n.trees = 100, predict.type = "prob")

	audit.lmr = mlr::train(classif.gbm, audit.task)
	audit.lmr = decorate(audit.lmr, invert_levels = TRUE)
	print(audit.lmr)

	adjusted = as.data.frame(predict(audit.lmr, newdata = audit))

	storeRds(audit.lmr, "WrappedGBMAdaBoostAuditNA")
	storeCsv(data.frame("Adjusted" = adjusted$response, "probability(0)" = adjusted$prob.0, "probability(1)" = adjusted$prob.1, check.names = FALSE), "WrappedGBMAdaBoostAuditNA")
}

set.seed(42)

generateWrappedGBMAdaBoostAudit()

iris = loadIrisCsv("Iris")

iris_x = iris[, -ncol(iris)]
iris_y = iris[, ncol(iris)]

predictGBMIris = function(iris.gbm){
	probabilities = predict(iris.gbm, newdata = iris_x, type = "response", n.trees = 7)
	probabilities = drop(probabilities)

	species = as.factor(apply(probabilities, 1, FUN = which.max))
	levels(species) = c("setosa", "versicolor", "virginica")

	result = data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE)

	return (result)
}

generateGBMFormulaIris = function(){
	iris.gbm = gbm(Species ~ ., data = iris, interaction.depth = 2, shrinkage = 0.1, n.trees = 7)
	print(iris.gbm)

	storeRds(iris.gbm, "GBMFormulaIris")
	storeCsv(predictGBMIris(iris.gbm), "GBMFormulaIris")
}

generateGBMIris = function(){
	iris.gbm = gbm.fit(x = iris_x, y = iris_y, distribution = "multinomial", interaction.depth = 2, shrinkage = 0.1, n.trees = 7, response.name = "Species")
	print(iris.gbm)

	storeRds(iris.gbm, "GBMIris")
	storeCsv(predictGBMIris(iris.gbm), "GBMIris")
}

set.seed(42)

generateGBMFormulaIris()
generateGBMIris()

generateTrainGBMFormulaIris = function(){
	iris.train = caret::train(Species ~ ., data = iris, method = "gbm", response.name = "Species")
	iris.train = verify(iris.train, newdata = sample_n(iris, 10))
	print(iris.train)

	species = predict(iris.train, newdata = iris)
	probabilities = predict(iris.train, newdata = iris, type = "prob")

	storeRds(iris.train, "TrainGBMFormulaIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "TrainGBMFormulaIris")
}

set.seed(42)

generateTrainGBMFormulaIris()

auto = loadAutoCsv("AutoNA")

auto_x = auto[, -ncol(auto)]
auto_y = auto[, ncol(auto)]

generateGBMFormulaAutoNA = function(){
	auto.gbm = gbm(mpg ~ ., data = auto, interaction.depth = 3, shrinkage = 0.1, n.trees = 100)
	print(auto.gbm)

	mpg = predict(auto.gbm, newdata = auto, n.trees = 100)

	storeRds(auto.gbm, "GBMFormulaAutoNA")
	storeCsv(data.frame("mpg" = mpg), "GBMFormulaAutoNA")
}

generateGBMAutoNA = function(){
	auto.gbm = gbm.fit(x = auto_x, y = auto_y, distribution = "gaussian", interaction.depth = 3, shrinkage = 0.1, n.trees = 100, response.name = "mpg")
	print(auto.gbm)

	mpg = predict(auto.gbm, newdata = auto_x, n.trees = 100)

	storeRds(auto.gbm, "GBMAutoNA")
	storeCsv(data.frame("mpg" = mpg), "GBMAutoNA")
}

set.seed(42)

generateGBMFormulaAutoNA()
generateGBMAutoNA()

auto.caret = auto
auto.caret$origin = as.integer(auto.caret$origin)

generateTrainGBMFormulaAutoNA = function(){
	auto.train = caret::train(mpg ~ ., data = auto.caret, method = "gbm", na.action = na.pass, response.name = "mpg")
	auto.train = verify(auto.train, newdata = sample_n(auto.caret, 50), na.action = na.pass)
	print(auto.train)

	mpg = predict(auto.train, newdata = auto.caret, na.action = na.pass)

	storeRds(auto.train, "TrainGBMFormulaAutoNA")
	storeCsv(data.frame("mpg" = mpg), "TrainGBMFormulaAutoNA")
}

set.seed(42)

generateTrainGBMFormulaAutoNA()
